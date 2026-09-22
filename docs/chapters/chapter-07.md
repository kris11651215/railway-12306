# 第7章 Linux部署与运维

> 项目：铁路智慧出行综合服务平台
> 状态：✅ 已完成
> 建议耗时：第11-12周，约4-6天
> 深度：Linux 排障 L3（场景化命令）；Docker/Compose/Nginx L2（会用、能改配置）；监控 L2-L3
> 一句话：把前面六章的"内存模式能跑"变成"一条 `docker compose up` 启动 MySQL + Redis + 应用 + Nginx"，再用日志、top、jstack、自定义指标端点把线上排障链路走通。

---

## 本章目标

| 目标 | 具体产出 | 验收方式 |
| --- | --- | --- |
| Linux 排障 L3 | 日志定位空指针、top+jstack 定位 CPU 高、ss 查端口、systemctl 管服务 | 能按场景说出命令链路与输出解读 |
| Docker | `Dockerfile` 多阶段构建 + `.dockerignore` | 镜像可构建（本机无 Docker，配置静态校验） |
| Compose | `docker-compose.yml` 编排 MySQL/Redis/应用/Nginx + 三个可选 profile | `docker compose config` 通过（无 Docker 时静态校验） |
| Nginx L2 | `deploy/nginx/nginx.conf` 反向代理 + 动态解析 + 负载均衡 | `nginx -t` 通过，代理到应用 |
| 监控 | `/internal/metrics` + Prometheus + Grafana + SkyWalking 方案 | 指标可抓取，看板可导入 |
| 文档 | 每步可复现的部署步骤 | 按文档顺序可完整复现 |

配套材料：

- 开头导图：`docs/mindmaps/chapter-07-start.mmd`
- 复盘导图：`docs/mindmaps/chapter-07-end.mmd`
- 应用与双模式开关：`src/main/resources/application.yml`（第4-6章）
- 数据库初始化：`sql/init.sql`（第2章）

---

## 7.0 环境与验证边界（如实说明）

| 项 | 本机状态 | 处理方式 |
| --- | --- | --- |
| Docker / docker compose | **未安装** | 不下载不安装；只产出 Dockerfile/compose/Nginx/监控配置，用 JUnit5 做配置静态守护测试 |
| Linux 环境 | 本机为 Windows | Linux 命令为**目标环境操作手册**，文中输出均标注"示例输出" |
| MySQL / Redis | 未运行 | compose 中通过 healthcheck 与 depends_on 编排，未实机启动 |
| Prometheus / Grafana / SkyWalking | 未安装 | 只产出配置与接入步骤，未实机拉起 |
| 8080 服务 | 未运行 | 未做 HTTP 验证 |

**验证方式**：`DeploymentConfigTest` 对 Dockerfile、compose、Nginx、Prometheus、Grafana 配置做存在性与关键指令静态校验；`MetricsControllerTest` 对指标端点做离线验证。Docker 编排的"实机启动"在具备 Docker 的机器上按 7.6 节步骤复现。

---

## 7.1 Linux 排查实战（场景驱动）

> 命令均为 Linux 目标环境执行；以下输出为**示例输出**，用于讲解"看到什么、说明什么、下一步做什么"。

### 7.1.1 场景一：接口返回 500，用日志定位空指针

**第 1 步：确认异常出口**。本项目 `GlobalExceptionHandler` 对未预期异常统一打印堆栈：

```java
log.error("系统异常", e);
```

**第 2 步：按关键字找堆栈**：

```bash
# 最近 200 行里找异常
tail -n 200 logs/railway-12306.log

# 全量找 NullPointerException 并显示行号与后 30 行
grep -n "NullPointerException" logs/railway-12306.log

# 直接看某次请求的完整调用链（按时间锚点）
grep -n "2026-09-15 20:31" logs/railway-12306.log
```

示例输出：

```text
20:31:05.412 ERROR 1 --- [http-nio-8080-exec-3] c.r.exception.GlobalExceptionHandler : 系统异常
java.lang.NullPointerException: Cannot invoke "com.railway.entity.Station.getId()" because "station" is null
    at com.railway.service.OrderService.createOrder(OrderService.java:62)
    at com.railway.controller.OrderController.createOrder(OrderController.java:34)
```

**第 3 步：解读堆栈**。关键看三点：

1. 异常类型：`NullPointerException`，哪个引用为 null（`station`）；
2. **第一个业务包帧**：`com.railway.service.OrderService.createOrder`，这就是要看的代码位置；
3. 行号：`OrderService.java:62`，直接定位。

**第 4 步：修复与验证**。该项目已对"站点不存在"做了业务校验（第3章），如果仍出现 NPE，说明新加的分支漏了校验；修复后补充单测，而不是只改日志。

**日志检索常用组合**：

```bash
grep -c "ERROR" logs/railway-12306.log                 # 错误条数
grep "ERROR" logs/railway-12306.log | tail -n 20       # 最近错误
grep -B5 -A30 "NullPointerException" logs/railway-12306.log   # 堆栈上下文
awk '/ERROR/ {print $1, $2, $NF}' logs/railway-12306.log | tail  # 只留时间与关键信息
```

**日志切割检查**（防止磁盘被日志写满）：

```bash
ls -lh logs/
du -sh logs/
```

### 7.1.2 场景二：CPU 飙高，top + jstack 定位代码行

**第 1 步：top 找进程**：

```bash
top -c
```

示例输出：

```text
  PID USER   PR  NI  VIRT   RES  %CPU %MEM  TIME+ COMMAND
 3210 root   20   0 4.2g   1.1g 398.7 13.8  12:31.44 java -jar app.jar
```

说明：进程 3210 的 CPU 接近 400%（多核累加），需要进一步定位线程。

**第 2 步：进程内按线程排序**：

```bash
top -Hp 3210
```

示例输出（某个线程占满一核）：

```text
  PID USER  PR  NI  VIRT  RES %CPU %MEM TIME+ COMMAND
 3245 root  20   0 4.2g  1.1g 99.9 13.8 5:02.11 java
```

**第 3 步：线程号转十六进制**（jstack 输出里的 nid 是十六进制）：

```bash
printf "%x\n" 3245        # 输出 cad，例如 0xcad
```

**第 4 步：jstack 导出线程栈并定位**：

```bash
jstack 3210 > /tmp/jstack-3210.txt
grep -n "nid=0xcad" -A 30 /tmp/jstack-3210.txt
```

示例输出：

```text
"http-nio-8080-exec-7" #41 daemon prio=5 os_prio=0 cpu=301245.12ms elapsed=310.55s tid=0x00007f... nid=0xcad runnable
    at com.railway.service.concurrency.GrabProcessor.deduct(GrabProcessor.java:88)
    at com.railway.service.concurrency.GrabProcessor.consume(GrabProcessor.java:54)
    ...
```

**第 5 步：结合代码判断**。常见 CPU 高的原因：

| 线程栈特征 | 常见原因 | 处理 |
| --- | --- | --- |
| 业务循环里跑满 CPU | 死循环/无界重试 | 加退避、加最大重试次数 |
| `GC task thread` 多且频繁 | 内存泄漏/堆太小 | `jstat -gcutil 3210 1000`、堆转储分析 |
| 大量 `BLOCKED` 线程等同一把锁 | 锁竞争/死锁 | `jstack` 搜 `deadlock`、缩小锁范围 |
| 正则回溯 | 复杂正则处理长文本 | 换正则或限制输入长度 |

**第 6 步（可选）：jstat 看 GC**：

```bash
jstat -gcutil 3210 1000 5
```

示例输出：

```text
  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT
  0.00  96.09  82.31  71.02  94.11  90.27   312    4.120     3    1.873    5.993
```

`FGC=3` 且 `GCT` 持续增长就要警惕内存问题。

> 记忆法：**top 找进程 → top -Hp 找线程 → printf 转 hex → jstack 找 nid → 看第一行业务帧**。

### 7.1.3 场景三：端口被占用 / 服务没起来

```bash
ss -lntp | grep 8080          # 推荐：ss 比 netstat 更快
netstat -lntp | grep 8080     # 老系统常用
lsof -i :8080                 # 看是哪个进程占用
ps -ef | grep java            # 看应用进程是否还在
```

判断逻辑：

- 有 LISTEN 但接口 404：可能起的是别的服务，核对 `curl -s localhost:8080/internal/metrics`；
- 没有 LISTEN 且 `docker ps` 看不到容器：`docker compose logs app` 看启动日志；
- Address already in use：换端口或先杀占用进程（生产先确认归属）。

### 7.1.4 场景四：磁盘满导致写日志失败

```bash
df -h                          # 看哪个挂载点满
du -sh /var/lib/docker /app/logs /var/log 2>/dev/null
find /app/logs -type f -size +100M -exec ls -lh {} \;
```

处理顺序：清理大日志（保留最近 N 天）→ 配置 logrotate 或 Docker `max-size` → 给数据盘扩容。本项目 compose 中给 MySQL/Redis/Prometheus/Grafana 都挂了命名卷，避免容器层写满宿主盘。

### 7.1.5 场景五：systemctl 管理服务

不使用 Docker 的裸机部署方式：

```bash
sudo systemctl start railway-12306       # 启动
sudo systemctl status railway-12306      # 状态与最近日志
sudo systemctl enable railway-12306      # 开机自启
sudo systemctl restart railway-12306     # 重启
journalctl -u railway-12306 -n 100 -f    # 跟日志（排查时）
```

对应的 `railway-12306.service` 关键项：

```ini
[Service]
User=railway
WorkingDirectory=/opt/railway
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar app.jar
Restart=on-failure
Environment=SPRING_PROFILES_ACTIVE=prod
```

### 7.1.6 grep / awk / sed 三件套

**grep：过滤**。找错误、找请求、找追踪号：

```bash
grep -n "系统异常" logs/railway-12306.log
grep -c "POST /api/order/grab" logs/railway-12306.log
grep -E "ERROR|WARN" logs/railway-12306.log | wc -l
```

**awk：按列统计**。Nginx 访问日志里统计状态码分布、慢请求：

```bash
awk '{print $9}' /var/log/nginx/access.log | sort | uniq -c | sort -rn
awk '$NF > 1 {print}' /var/log/nginx/access.log | head    # rt>1s 的慢请求
awk '{sum+=$NF; count++} END {print "avg", sum/count}' /var/log/nginx/access.log
```

**sed：批量替换与提取**。日志脱敏、版本号替换：

```bash
sed -i 's/1.0.0/1.0.1/g' config/*.yml
sed -n '100,120p' logs/railway-12306.log
sed 's/\(1[0-9]\{5\}\)[0-9]\{8\}\([0-9xX]\{4\}\)/\1********\2/g' logs/app.log   # 证件号脱敏
```

**组合技**：找最慢的三个接口：

```bash
awk '{print $7, $NF}' /var/log/nginx/access.log | sort -k2 -nr | head -3
```

---

## 7.2 Dockerfile：多阶段构建

### 7.2.1 是什么

Dockerfile 是镜像的"构建脚本"。本项目采用**多阶段构建**：第一阶段用 Maven 镜像编译打包，第二阶段只拷贝 jar 到 JRE 镜像，产物更小、又不把源码和 Maven 缓存带进运行镜像。

### 7.2.2 逐段解读

```dockerfile
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/railway-12306-0.0.1-SNAPSHOT.jar app.jar

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=10 \
    CMD curl -fsS http://localhost:8080/internal/metrics || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

要点：

1. **先 COPY pom.xml 再依赖下载**：pom 不改时这一层命中缓存，改代码不用重新下载依赖；
2. **`-DskipTests`**：镜像构建时不跑测试（测试在 CI 的 `mvn test` 阶段做），加快构建；
3. **运行镜像只装 curl**：给 HEALTHCHECK 用；JRE 基础镜像不含 curl；
4. **`TZ=Asia/Shanghai`**：保证订单时间、调度任务时间正确；
5. **`JAVA_OPTS` 用环境变量**：compose 可覆盖堆大小；
6. **`ENTRYPOINT` 用 `sh -c`**：让 `$JAVA_OPTS` 生效（exec 形式不会展开变量）。

### 7.2.3 为什么用 `.dockerignore`

```text
target
.git
.idea
*.iml
docs
*.md
docker-compose.yml
deploy
```

不忽略 `target/` 的话，几百 MB 的构建产物会被塞进构建上下文，构建变慢甚至失败；忽略 docs 与 deploy 可减小上下文。

### 7.2.4 常用命令

```bash
docker build -t railway-12306:latest .        # 构建
docker images railway-12306                   # 看镜像大小
docker run --rm -p 8080:8080 railway-12306:latest   # 前台运行（学习用）
docker run -d -p 8080:8080 --name railway-app railway-12306:latest
docker logs -f railway-app                    # 看日志
docker exec -it railway-app sh                # 进容器
docker inspect --format='{{.State.Health.Status}}' railway-app   # 健康状态
```

### 7.2.5 怎么排错

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 构建卡在 dependency 下载 | 网络/镜像源慢 | 配 Maven 镜像源，或先本地 `mvn package` 用缓存 |
| `COPY failed: no source files` | 上下文不对或 `.dockerignore` 排除了 | 确认 `docker build` 的 context 与 ignore |
| 容器起来就退出 | 端口冲突/数据库连不上 | `docker logs` 看异常；先起 MySQL |
| 健康检查一直 unhealthy | `/internal/metrics` 404 或 curl 不存在 | 确认端点存在、运行镜像装了 curl |
| `OOMKilled` | 堆超限 | 调 `JAVA_OPTS -Xmx` 并给容器内存限制留余量 |

---

## 7.3 docker-compose：一条命令启动全项目

### 7.3.1 服务清单

| 服务 | 镜像 | 端口 | 作用 | 默认启动 |
| --- | --- | --- | --- | --- |
| `mysql` | mysql:8.4 | 3306 | 业务库，首次启动自动导入 `sql/init.sql` | ✅ |
| `redis` | redis:7-alpine | 6379 | 分布式锁与库存缓存（第4章） | ✅ |
| `app` | 本地构建 | 8080 | Spring Boot 应用 | ✅ |
| `nginx` | nginx:1.27-alpine | 80 | 反向代理与负载均衡 | ✅ |
| `prometheus` | prom/prometheus:v2.54.1 | 9090 | 指标采集 | profile `monitoring` |
| `grafana` | grafana/grafana:11.2.0 | 3000 | 监控看板 | profile `monitoring` |
| `skywalking-oap/ui` | apache/skywalking:* | 11800/12800/8090 | 链路追踪 | profile `tracing` |
| `rocketmq-namesrv/broker` | apache/rocketmq:5.3.1 | 9876/10911 | 消息队列（接入预留） | profile `mq` |

### 7.3.2 为什么这样编排

- **healthcheck + depends_on**：应用等到 MySQL、Redis 健康后再启动，避免"启动即报连接失败"；Nginx 也以 `condition: service_healthy` 等待应用，健康检查复用 Dockerfile 中的 `/internal/metrics` 探针：

```yaml
depends_on:
  mysql:
    condition: service_healthy
  redis:
    condition: service_healthy
```

- **数据卷**：`mysql-data`、`redis-data` 等命名卷把数据放在容器外，`docker compose down` 不丢数据（加 `-v` 才清）；
- **初始化脚本**：`./sql/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro`，MySQL 首次初始化自动建库建表灌数据；
- **环境变量对接双模式**：把第4-6章的开关切到真实组件：

```yaml
RAILWAY_REDIS_MODE: redis        # railway.redis.mode
RAILWAY_GRAB_ORDER_STORE: db     # railway.grab.order-store
RAILWAY_ROUTE_MODE: db           # railway.route.mode
RAILWAY_AI_DATA_MODE: db         # railway.ai.data-mode
RAILWAY_AI_MODE: ${RAILWAY_AI_MODE:-mock}
DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY:-}
```

Spring Boot 的 relaxed binding 会把 `RAILWAY_REDIS_MODE` 映射到 `railway.redis.mode`，不需要改代码。

- **profiles 按需启动**：默认只起核心四件套，重量级的 MQ、SkyWalking、Prometheus 用 profile 分开，避免开发机被拖垮；
- **安全提示**：compose 中的 MySQL/Grafana 密码为本地开发默认值（`railway123`），生产必须通过 `MYSQL_ROOT_PASSWORD`、`GRAFANA_PASSWORD` 环境变量覆盖，且不要提交 `.env` 到 Git；LLM Key 只通过环境变量注入；
- **消息队列说明**：第4章实际用的是内存队列（`GrabQueue`），`mq` profile 里的 RocketMQ 是第8章/生产接入的预留编排，配置好但应用尚未依赖它——文档如实标注，不把它算进"已联调"。

### 7.3.3 命令

```bash
docker compose up -d                     # 启动核心服务（首次会构建镜像）
docker compose up -d --profile monitoring   # 额外启动 Prometheus + Grafana
docker compose up -d --profile tracing      # 额外启动 SkyWalking
docker compose up -d --profile mq           # 额外启动 RocketMQ
docker compose ps                        # 查看状态与健康
docker compose logs -f app               # 跟应用日志
docker compose logs -f nginx             # 跟 Nginx 日志
docker compose down                      # 停止并删除容器（保留数据卷）
docker compose down -v                   # 连数据卷一起删（谨慎）
docker compose config                     # 校验 compose 文件语法
docker compose up -d --scale app=2       # 应用水平扩容到 2 实例（配合 Nginx 动态解析）
```

> 样例数据提示：`sql/init.sql` 的库存与候补样例集中在 2026-04-15 ~ 2026-05-16；切到 `db` 模式做换乘/抢票演示时请带上 `date=2026-04-15`（或先重新灌入当天数据），否则换乘图会走"无票价按里程估算"的兜底逻辑。

### 7.3.4 怎么排错

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| app 反复重启 | MySQL 还没初始化完 | 看 healthcheck；`docker compose logs mysql` |
| 3306/6379 被本机占用 | 本地已装同端口服务 | 改映射端口，如 `3307:3306` |
| 卷里是旧数据 | init.sql 只在首次初始化执行 | `down -v` 后再 `up`（会清数据） |
| profile 服务没起来 | 忘了 `--profile` | 命令加 `--profile monitoring` |
| `--scale` 后请求仍打一台 | Nginx 未用动态解析 | 见 7.4 的 `resolver` 配置 |

---

## 7.4 Nginx 反向代理

### 7.4.1 配置逐段解读

```nginx
resolver 127.0.0.11 valid=10s ipv6=off;
set $railway_backend http://app:8080;

server {
    listen 80;
    server_name _;

    location = /nginx-health { access_log off; return 200 "ok\n"; }

    location /internal/ { return 403; }

    location / {
        proxy_pass $railway_backend;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";
        proxy_connect_timeout 3s;
        proxy_read_timeout 30s;
        proxy_send_timeout 30s;
    }
}
```

关键点：

1. **`resolver 127.0.0.11`**：Docker 内置 DNS。用变量 `$railway_backend` 配合 `proxy_pass`，Nginx 会在运行时重新解析 `app`，执行 `--scale app=2` 后自动轮询到两个容器；直接写 `upstream app:8080` 只在启动时解析一次，扩容不生效；
2. **`proxy_set_header X-Forwarded-For`**：应用与日志能看到真实客户端 IP；
3. **`location /internal/ { return 403; }`**：指标端点只允许内网 Prometheus 访问，不对公网暴露；
4. **`/nginx-health`**：给 compose 健康检查用的轻量探针；
5. **超时**：连接 3s、读写 30s，与抢票接口的快速失败策略一致；
6. **gzip**：JSON 响应开启压缩，减少带宽。

### 7.4.2 负载均衡验证

```bash
docker compose up -d --scale app=2
for i in $(seq 1 6); do curl -s http://localhost/internal/none 2>/dev/null; done
docker compose logs nginx | grep upstream     # 看 upstream 地址分布
```

`access.log` 的 `upstream=` 字段会记录实际转发到的容器 IP，可以直接确认是否轮询。

### 7.4.3 Nginx 配置校验

```bash
docker compose exec nginx nginx -t           # 语法检查
docker compose exec nginx nginx -s reload    # 不改容器热加载配置
```

本机无 Docker/Nginx，本章用 `DeploymentConfigTest` 对 `resolver`、`proxy_pass`、`/internal/` 拒绝、健康探针等关键指令做静态校验。

### 7.4.4 怎么排错

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 502 Bad Gateway | app 没起来/端口错 | `docker compose ps` 查 app 健康 |
| 504 Gateway Timeout | 后端处理超 30s | 查慢接口；必要时调 `proxy_read_timeout` |
| 上传大 body 413 | 超过 `client_max_body_size` | 调大限制 |
| 扩容不轮询 | 用了 upstream 写死 | 改 `resolver` + 变量写法 |
| 拿到内网 IP 而不是客户端 IP | 没转发头 | 检查 `X-Real-IP`/`X-Forwarded-For` |

---

## 7.5 监控：Prometheus + Grafana + SkyWalking

### 7.5.1 为什么自实现 `/internal/metrics`

标准做法是引入 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`。本章环境无法联网下载新依赖，因此用 `java.lang.management` 写了一个**轻量 Prometheus 文本格式端点**（`MetricsController`），覆盖 JVM 核心指标：

```text
railway_up                                    服务存活
railway_jvm_heap_used_bytes                   堆已用
railway_jvm_heap_committed_bytes              堆已提交
railway_jvm_heap_max_bytes                    堆上限
railway_jvm_nonheap_used_bytes                非堆已用
railway_jvm_threads                           线程数
railway_jvm_uptime_seconds                    运行时长
railway_jvm_gc_collections_total              GC 次数
railway_jvm_gc_time_millis_total              GC 耗时
railway_system_load_average                   系统负载
railway_system_processors                     CPU 核数
```

> 说明：`railway_system_load_average` 在部分操作系统（如 Windows）上不可用时会返回 `-1.0`，Linux 生产环境显示真实 1 分钟负载；看板对该指标仅作趋势参考，核心告警用 `railway_up` 与堆内存比例。

生产替换为 Actuator 的步骤：

1. `pom.xml` 增加 `spring-boot-starter-actuator` 与 `micrometer-registry-prometheus`；
2. `application.yml` 暴露 `management.endpoints.web.exposure.include: health,metrics,prometheus`；
3. Prometheus 的 `metrics_path` 改为 `/actuator/prometheus`；
4. 删除或保留 `/internal/metrics` 作为业务自定义指标入口。

### 7.5.2 Prometheus 采集

`deploy/prometheus/prometheus.yml`：

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: railway-app
    metrics_path: /internal/metrics
    static_configs:
      - targets: ["app:8080"]
        labels:
          application: railway-12306
```

验证：

```bash
open http://localhost:9090/targets        # Targets 页看 railway-app 是否 UP
curl -s http://localhost:9090/api/v1/query?query=railway_up   # 查询指标
```

告警示例（`deploy/prometheus/` 可新增 `alert-rules.yml`）：

```yaml
groups:
  - name: railway
    rules:
      - alert: RailwayAppDown
        expr: railway_up == 0
        for: 1m
        labels: { severity: critical }
        annotations: { summary: "应用存活指标为0" }
      - alert: HeapUsageHigh
        expr: railway_jvm_heap_used_bytes / railway_jvm_heap_max_bytes > 0.85
        for: 5m
        labels: { severity: warning }
```

### 7.5.3 Grafana 看板

Grafana 用 provisioning 自动加载数据源与看板，无需手工点：

```text
deploy/grafana/
├── provisioning/
│   ├── datasources/prometheus.yml     # 数据源指向 http://prometheus:9090
│   └── dashboards/dashboard.yml       # 看板目录 /var/lib/grafana/dashboards
└── dashboards/railway-overview.json   # 应用概览看板（6 个面板）
```

启动：

```bash
docker compose up -d --profile monitoring
open http://localhost:3000       # 默认 admin / railway123（GF_SECURITY_ADMIN_PASSWORD）
```

看板包含：应用存活、线程数、运行时长、GC 次数、堆内存曲线、系统负载。

### 7.5.4 SkyWalking 链路追踪

**组件**：OAP（接收链路数据）+ UI（展示）+ Java Agent（附着到应用）。

```bash
docker compose up -d --profile tracing
open http://localhost:8090       # SkyWalking UI
```

应用侧接入（把 agent 解压到宿主机 `/opt/skywalking-agent` 并挂载）：

```bash
docker run -d --name railway-app \
  --network railway-12306_railway-net \
  -p 8080:8080 \
  -e JAVA_TOOL_OPTIONS="-javaagent:/skywalking/agent/skywalking-agent.jar -Dskywalking.agent.service_name=railway-12306 -Dskywalking.collector.backend_service=skywalking-oap:11800" \
  -v /opt/skywalking-agent:/skywalking/agent:ro \
  railway-12306:latest
```

或者使用 `deploy/skywalking/agent.env`：

```text
JAVA_TOOL_OPTIONS=-javaagent:/skywalking/agent/skywalking-agent.jar -Dskywalking.agent.service_name=railway-12306 -Dskywalking.collector.backend_service=skywalking-oap:11800
```

```bash
docker run -d --env-file deploy/skywalking/agent.env ... railway-12306:latest
```

说明：本机未安装 Docker、未下载 SkyWalking Agent，接入步骤为**代码/配置就绪、未实机验证**。真实接入时优先用 SkyWalking 官方 agent 分发包，并按版本核对 OAP 端口（默认 11800）。

### 7.5.5 怎么排错

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| Target DOWN | Prometheus 连不上 app 或路径错 | `docker compose logs prometheus`，容器内 `wget app:8080/internal/metrics` |
| 指标为 0 或缺失 | 端点被 Nginx 拦了 | Prometheus 直连 app，不走 Nginx |
| Grafana 无数据源 | provisioning 目录挂载错 | 检查 compose 挂载路径 |
| SkyWalking 没数据 | agent 未挂载/服务名不对 | 看应用日志中 agent 输出与 OAP 连通性 |

---

## 7.6 验收复现步骤

> 以下步骤在具备 Docker 的 Linux/macOS/Windows(Desktop) 环境执行；本机无 Docker，未实机执行。

**验收 1：docker-compose up 启动全项目**

```bash
docker compose config          # 1) 语法校验
docker compose up -d           # 2) 启动 mysql/redis/app/nginx
docker compose ps              # 3) 四个服务 healthy
curl -s http://localhost/api/trains?from=北京南\&to=上海虹桥\&date=2026-04-15
curl -s http://localhost/internal/... # 4) 经 Nginx 访问应 403（指标仅内网）
curl -s http://localhost:8080/internal/metrics | head    # 5) 直连看指标
```

预期：车次查询返回 G1，Nginx 80 端口可用，`/internal` 对外 403。

**验收 2：日志定位空指针**

```bash
docker compose logs app | grep -n "NullPointerException" -A 20
```

预期：能看到异常类型、业务包第一帧与行号；结合 `GlobalExceptionHandler` 的"系统异常"日志定位。

**验收 3：top + jstack 定位 CPU 高**

```bash
docker exec -it railway-app sh
top -c                 # 找 java 进程 PID
top -Hp <PID>          # 找高 CPU 线程 TID
printf "%x\n" <TID>    # 转 hex
jstack <PID> > /tmp/jstack.txt
grep -n "nid=0x<hex>" -A 30 /tmp/jstack.txt
```

预期：得到线程名与业务栈帧，据此优化代码。

**验收 4：Nginx 反向代理**

```bash
docker compose exec nginx nginx -t
curl -s http://localhost/nginx-health          # ok
docker compose up -d --scale app=2
docker compose logs nginx | grep upstream      # 出现多个 upstream IP
```

预期：语法 OK、健康探针 200、扩容后轮询两个实例。

---

## 7.7 测试与验证

### 7.7.1 本章新增测试（6 个）

| 测试类 | 数量 | 覆盖 |
| --- | --- | --- |
| `MetricsControllerTest` | 1 | 指标端点 Prometheus 文本格式与关键指标 |
| `DeploymentConfigTest` | 5 | Dockerfile 多阶段与健康检查、compose 服务/profile/双模式环境变量、Nginx resolver 与代理、Prometheus 抓取路径、Grafana 看板 JSON |

### 7.7.2 运行结果

```bash
.\mvnw.cmd test
```

```text
Tests run: 80, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

累计 80 个测试通过（第0-6章 74 个 + 本章 6 个）。

### 7.7.3 未实机验证项（如实标注）

- Docker 镜像构建与 `docker compose up` 未执行（本机无 Docker）；
- Nginx `nginx -t` 未执行（无 Nginx）；
- Prometheus/Grafana/SkyWalking 未实际拉起；
- Linux 排查命令为文档与静态配置级验证，未在 Linux 目标机执行。

替代验证：配置静态守护测试 + 7.6 节可复现步骤。

---

## 7.8 本章代码地图

| 文件 | 职责 |
| --- | --- |
| `Dockerfile` | 多阶段构建、健康检查、JVM 参数 |
| `.dockerignore` | 缩小构建上下文 |
| `docker-compose.yml` | MySQL/Redis/App/Nginx + monitoring/tracing/mq profiles |
| `deploy/nginx/nginx.conf` | 反向代理、动态解析、负载均衡、内网端点保护 |
| `deploy/prometheus/prometheus.yml` | 指标抓取配置 |
| `deploy/grafana/provisioning/*` | 数据源与看板自动加载 |
| `deploy/grafana/dashboards/railway-overview.json` | 应用概览看板 |
| `deploy/skywalking/agent.env` | SkyWalking Agent 环境变量示例 |
| `src/main/java/com/railway/controller/MetricsController.java` | `/internal/metrics` 指标端点 |
| `docs/mindmaps/chapter-07-start.mmd` / `chapter-07-end.mmd` | 本章导图 |

---

## 7.9 高频面试问答

1. **"Dockerfile 为什么要多阶段构建？"**
   编译依赖（Maven/JDK）只在构建阶段，运行镜像只保留 JRE + jar，镜像从 700MB+ 降到 200MB 级，攻击面也更小。

2. **"Compose 里 depends_on 为什么不够？"**
   默认 `depends_on` 只保证启动顺序，不保证对方"可用"；要配合 `healthcheck` + `condition: service_healthy`，否则应用可能先于 MySQL 就绪而启动失败。

3. **"Nginx 怎么支持应用扩容？"**
   用 Docker 内置 DNS `resolver 127.0.0.11` + 变量 `proxy_pass $backend`，Nginx 运行时重新解析服务名，`--scale app=2` 后自动轮询；写死 upstream 只在启动时解析一次。

4. **"线上 CPU 高怎么排查？"**
   `top` 找进程 → `top -Hp` 找线程 → `printf %x` 转十六进制 → `jstack` 搜 `nid` → 看第一行业务帧；GC 问题再用 `jstat -gcutil`。

5. **"日志太多怎么处理？"**
   结构化日志 + 分级输出，grep/awk 快速过滤；文件按天切割并设置保留天数，Docker 侧限制日志大小；关键链路带 traceId 方便串联。

6. **"监控为什么先用 Prometheus + Grafana？"**
   拉模式部署简单、生态成熟、指标模型统一；应用只要暴露 `/metrics` 文本即可接入。链路追踪再用 SkyWalking 补充。

7. **"容器里 JVM 参数怎么给？"**
   用环境变量 `JAVA_OPTS` 注入，`ENTRYPOINT` 用 `sh -c` 展开；容器内存限制要大于 `-Xmx` 加元空间/线程栈的余量，避免 OOMKilled。

---

## 7.10 自检三问

1. **是什么**：Dockerfile 把应用打成镜像，Compose 编排多服务并管理依赖与数据卷，Nginx 做反代与负载均衡，Prometheus/Grafana 做指标监控，`/internal/metrics` 暴露 JVM 指标。
2. **为什么**：一条命令复现整套环境，消除"我这里能跑"；健康检查保证启动顺序；Nginx 屏蔽内网端点并支持扩容；监控指标让排障从"猜"变成"看"。
3. **怎么排错**：起不来先看 `docker compose logs` 与健康状态；接口异常用日志定位业务帧；CPU 高按 top→Hp→hex→jstack 链路；监控 Target DOWN 先测容器内连通性；Nginx 502 查后端、504 查超时。

---

## 7.11 下一章预告

**第8章 项目收尾与铁路局考核包装**：把八章能力沉淀为可展示的成果——Swagger/Knife4j 接口文档、JUnit+Mockito 测试体系与覆盖率、Git 提交规范、面向铁路局技术岗的 README（三句话讲清业务痛点 + STAR 亮点 + 量化指标）、部署链接与项目缘起。最终产出总复盘导图 `99-final.mmd`，完成从"能跑"到"能讲、能面"的收尾。
