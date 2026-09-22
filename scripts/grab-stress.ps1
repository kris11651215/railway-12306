param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Concurrency = 100,
    [int]$Stock = 10,
    [ValidateSet("sync", "async")]
    [string]$Mode = "async",
    [string]$TrainNo = "G1",
    [string]$TravelDate = "2026-04-15",
    [string]$FromStation = "北京南",
    [string]$ToStation = "上海虹桥",
    [string]$SeatType = "二等座",
    [string]$ResultFile = ""
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
[System.Net.ServicePointManager]::DefaultConnectionLimit = 1000
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

function New-JsonContent([string]$json) {
    return New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, "application/json")
}

function Invoke-JsonGet([System.Net.Http.HttpClient]$client, [string]$url) {
    $response = $client.GetAsync($url).Result
    $text = $response.Content.ReadAsStringAsync().Result
    return $text | ConvertFrom-Json
}

function Invoke-JsonPost([System.Net.Http.HttpClient]$client, [string]$url, [string]$json) {
    $response = $client.PostAsync($url, (New-JsonContent $json)).Result
    $text = $response.Content.ReadAsStringAsync().Result
    return $text | ConvertFrom-Json
}

$client = New-Object System.Net.Http.HttpClient
$client.Timeout = New-Object System.TimeSpan(0, 0, 30)

Write-Host "== 铁路抢票并发压测（$Mode 模式） =="
Write-Host "服务地址: $BaseUrl  并发: $Concurrency  库存: $Stock"

try {
    $hello = $client.GetAsync("$BaseUrl/hello").Result
    if (-not $hello.IsSuccessStatusCode) {
        throw "hello 返回 $($hello.StatusCode)"
    }
} catch {
    Write-Host "[跳过] 服务未启动或不可达: $BaseUrl"
    Write-Host "先启动: .\mvnw.cmd spring-boot:run 或 java -jar target\railway-12306-0.0.1-SNAPSHOT.jar"
    exit 2
}

$reset = Invoke-JsonPost $client "$BaseUrl/api/order/grab/reset" "{}"
if ($reset.code -ne 0) {
    throw "reset 失败: $($reset.message)"
}
$warmupBody = @{
    trainNo     = $TrainNo
    travelDate  = $TravelDate
    fromStation = $FromStation
    toStation   = $ToStation
    seatType    = $SeatType
    totalCount  = $Stock
} | ConvertTo-Json -Compress
$warmupResponse = $client.PostAsync("$BaseUrl/api/order/grab/stock/warmup", (New-JsonContent $warmupBody)).Result
$warmup = $warmupResponse.Content.ReadAsStringAsync().Result | ConvertFrom-Json
Write-Host "预热库存: remaining=$($warmup.data.remaining) key=$($warmup.data.stockKey)"

$tasks = New-Object System.Collections.ArrayList
$syncFlag = if ($Mode -eq "sync") { "true" } else { "false" }
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
for ($i = 0; $i -lt $Concurrency; $i++) {
    $body = @{
        userId        = 9000 + $i
        trainNo       = $TrainNo
        travelDate    = $TravelDate
        fromStation   = $FromStation
        toStation     = $ToStation
        seatType      = $SeatType
        passengerName = "抢票旅客$i"
    } | ConvertTo-Json -Compress
    $task = $client.PostAsync("$BaseUrl/api/order/grab?sync=$syncFlag", (New-JsonContent $body))
    [void]$tasks.Add($task)
}

try {
    [System.Threading.Tasks.Task]::WaitAll($tasks.ToArray())
} catch {
    Write-Host "[警告] 部分请求异常: $($_.Exception.Message)"
}
$stopwatch.Stop()
$submitMillis = $stopwatch.ElapsedMilliseconds

$accepted = 0
$rejected = 0
foreach ($task in $tasks) {
    if ($task.IsFaulted) {
        $rejected++
        continue
    }
    $text = $task.Result.Content.ReadAsStringAsync().Result
    try {
        $parsed = $text | ConvertFrom-Json
    } catch {
        $rejected++
        continue
    }
    if ($parsed.code -eq 0) {
        $accepted++
    } else {
        $rejected++
    }
}

$drainMillis = $submitMillis
if ($Mode -eq "async") {
    $drainWatch = [System.Diagnostics.Stopwatch]::StartNew()
    $deadline = (Get-Date).AddSeconds(60)
    do {
        $stats = Invoke-JsonGet $client "$BaseUrl/api/order/grab/stats"
        if (($stats.data.success + $stats.data.failed) -ge $Concurrency) { break }
        Start-Sleep -Milliseconds 50
    } while ((Get-Date) -lt $deadline)
    $drainWatch.Stop()
    $drainMillis = $submitMillis + $drainWatch.ElapsedMilliseconds
} else {
    $stats = Invoke-JsonGet $client "$BaseUrl/api/order/grab/stats"
}

$stats = Invoke-JsonGet $client "$BaseUrl/api/order/grab/stats"
$stockUrl = "$BaseUrl/api/order/grab/stock?trainNo=$TrainNo&travelDate=$TravelDate&fromStation=$FromStation&toStation=$ToStation&seatType=$SeatType"
$stockView = Invoke-JsonGet $client ([System.Uri]::EscapeUriString($stockUrl))

$submitQps = if ($submitMillis -gt 0) { [Math]::Round($Concurrency * 1000.0 / $submitMillis, 2) } else { 0 }
$endToEndQps = if ($drainMillis -gt 0) { [Math]::Round($Concurrency * 1000.0 / $drainMillis, 2) } else { 0 }
$avgResponseMillis = if ($Concurrency -gt 0) { [Math]::Round($submitMillis / [double]$Concurrency, 2) } else { 0 }

$lines = @()
$lines += "抢票并发压测结果（$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')）"
$lines += "模式: $Mode  并发: $Concurrency  库存: $Stock"
$lines += "提交耗时: $submitMillis ms  提交QPS: $submitQps  端到端耗时: $drainMillis ms  端到端QPS: $endToEndQps"
$lines += "请求受理成功: $accepted  请求受理失败: $rejected  平均受理耗时: $avgResponseMillis ms"
$lines += "服务端submitted: $($stats.data.submitted)  服务端success: $($stats.data.success)  服务端failed: $($stats.data.failed)"
$lines += "订单数: $($stats.data.orders)  剩余库存: $($stockView.data.remaining)"
$report = $lines -join [Environment]::NewLine
Write-Host ""
Write-Host $report

if ($ResultFile -ne "") {
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($ResultFile, $report, $utf8)
    Write-Host "结果已写入: $ResultFile"
}

$client.Dispose()
