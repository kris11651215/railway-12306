# API 设计文档

> 项目：铁路智慧出行综合服务平台
> 版本：v1.4（第8章·全项目收尾）　服务地址：`http://localhost:8080`　编码：UTF-8
> 实现范围：车次查询、下单扣库存、高并发抢票、换乘路径规划、站内路径推荐、AI 智能购票助手（Function Calling）、候补加开建议、本地 RAG 知识问答、交通接驳引导、监控指标与 OpenAPI 文档

---

## 1. 通用约定

### 1.1 请求约定

| 项 | 约定 |
| --- | --- |
| 协议 | HTTP/1.1 |
| 请求体 | `Content-Type: application/json; charset=UTF-8` |
| 时间格式 | 日期 `yyyy-MM-dd`；时间 `HH:mm:ss`；日期时间 `yyyy-MM-ddTHH:mm:ss` |
| 中文参数 | URL 中需 percent-encode（curl 可用 `--data-urlencode` 或直接编码后的 URL） |
| 认证 | 暂未启用，后续章节引入 Spring Security 后升级 |

### 1.2 统一响应结构

所有接口无论成功失败，HTTP 状态码均为 200，业务结果由 body 中的 `code` 表达：

```json
{
  "code": 0,
  "message": "成功",
  "data": {},
  "timestamp": 1789399182922
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | 业务码，0 表示成功，非 0 见错误码表 |
| `message` | string | 提示信息，失败时可直接展示给用户 |
| `data` | object / array / null | 业务数据，失败时为 null |
| `timestamp` | long | 服务端毫秒时间戳 |

### 1.3 错误码表

| code | 含义 | 触发场景 |
| --- | --- | --- |
| 0 | 成功 | 正常返回 |
| 400 | 参数错误 | 缺参、类型转换失败、JSON 非法、出发到达相同 |
| 1001 | 车站不存在 | `from` / `to` 站名查不到 |
| 1002 | 车次不存在或不经过该区间 | 下单时车次无效或区间不符 |
| 2001 | 余票不足 | 区间内有区段 `remaining_count < 1` |
| 2002 | 订单创建失败 | 扣减影响行数异常 / 模拟回滚测试 / 获取锁超时 |
| 2003 | 抢票队列已满 | 内存/真实 MQ 队列达到容量上限 |
| 2004 | 抢票请求不存在 | 查询的 requestId 无记录或已过期清理 |
| 3001 | 无可行换乘方案 | 换乘图搜索不到满足阈值与次数限制的路径 |
| 500 | 系统内部错误 | 未预期异常，日志记录堆栈 |

---

## 2. 车次查询

### 2.1 基本信息

| 项 | 值 |
| --- | --- |
| 路径 | `GET /api/trains` |
| 功能 | 按出发站、到达站、日期查询可售车次及余票、票价 |
| 实现 | `TrainController.queryTrains` → `TrainService` → `TrainMapper.queryTrains` |

### 2.2 请求参数（query）

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `from` | string | 是 | - | 出发站名，如 `北京南`（精确匹配） |
| `to` | string | 是 | - | 到达站名，如 `上海虹桥`（精确匹配） |
| `date` | date | 是 | - | 乘车日期，`yyyy-MM-dd` |
| `seatType` | string | 否 | `二等座` | 席别，如 `二等座`、`一等座` |
| `trainType` | string | 否 | 全部 | 车次等级：`G/D/C/Z/T/K/P` |

### 2.3 业务规则

1. `from`、`to`、`date` 缺一不可；
2. `from` 与 `to` 不能相同；
3. 出发站、到达站必须存在于 `station` 表；
4. 只返回停运状态为开行（`train.status=1`）且经停顺序满足 `from 站序 < to 站序` 的车次；
5. 余票 = 乘车区间内所有原子区段的最小 `remaining_count`（席位复用模型，见《01-database-design.md》第 2 节）；
6. 票价 = 乘车区间内所有原子区段 `price` 之和；
7. 结果按出发站发车时间升序。

### 2.4 请求示例

```bash
curl "http://localhost:8080/api/trains?from=北京南&to=上海虹桥&date=2026-04-15"
curl "http://localhost:8080/api/trains?from=北京南&to=上海虹桥&date=2026-04-15&trainType=G"
```

### 2.5 响应示例

```json
{
  "code": 0,
  "message": "成功",
  "data": [
    {
      "trainId": 1,
      "trainNo": "G1",
      "trainType": "G",
      "trainTypeName": "高速动车组",
      "direction": "DOWN",
      "directionName": "下行",
      "bureau": "上海局集团",
      "fromStation": "北京南",
      "toStation": "上海虹桥",
      "departureTime": "09:00:00",
      "arrivalTime": "13:28:00",
      "durationMinutes": 268,
      "mileage": 1318,
      "seatType": "二等座",
      "remainingCount": 3,
      "totalPrice": 716.50
    }
  ],
  "timestamp": 1789399182922
}
```

### 2.6 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `trainId` | long | 车次主键 |
| `trainNo` | string | 车次号，如 G1 |
| `trainType` / `trainTypeName` | string | 等级码 / 中文名 |
| `direction` / `directionName` | string | `UP` 上行 / `DOWN` 下行及中文 |
| `bureau` | string | 担当路局 |
| `fromStation` / `toStation` | string | 实际乘车站（来自请求区间） |
| `departureTime` / `arrivalTime` | time | 出发站发车、到达站到点 |
| `durationMinutes` | int | 历时（分钟），已处理跨天 `day_offset` |
| `mileage` | int | 区间里程（公里） |
| `seatType` | string | 本次查询席别 |
| `remainingCount` | int | 区间余票（各原子段最小值） |
| `totalPrice` | decimal | 区间总价（各原子段之和） |

### 2.7 错误响应

| 场景 | 响应 |
| --- | --- |
| 到达站不存在 | `{"code":1001,"message":"到达站不存在：火星站","data":null}` |
| 出发到达相同 | `{"code":400,"message":"出发站与到达站不能相同","data":null}` |
| 缺少 date | `{"code":400,"message":"参数错误：Required request parameter 'date' ...","data":null}` |

---

## 3. 创建订单（下单扣库存）

### 3.1 基本信息

| 项 | 值 |
| --- | --- |
| 路径 | `POST /api/orders` |
| 功能 | 在单个事务内锁定并扣减区间库存、创建待支付订单 |
| 实现 | `OrderController.createOrder` → `OrderService.createOrder`（`@Transactional`） |

### 3.2 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | long | 是 | 用户 ID |
| `trainNo` | string | 是 | 车次号，如 `G1` |
| `travelDate` | date | 是 | 乘车日期 |
| `fromStation` | string | 是 | 出发站名 |
| `toStation` | string | 是 | 到达站名 |
| `seatType` | string | 否 | 默认 `二等座` |
| `passengerName` | string | 是 | 乘客姓名 |
| `passengerIdCard` | string | 否 | 证件号，入库前脱敏（`110101********1234`） |
| `failAfterDeduct` | boolean | 否 | 测试开关：为 `true` 时在扣库存后抛异常，用于验证事务回滚 |

### 3.3 业务规则

1. 车站、车次必须存在，且车次经停满足 `from 站序 < to 站序`；
2. 事务内先 `SELECT ... FOR UPDATE` 锁定区间内全部库存行，再逐段校验余票；
3. 扣减语句带 `remaining_count >= 1`，影响行数必须等于区段数，否则整体失败；
4. 库存扣减与订单写入必须同时成功或同时回滚（`rollbackFor = Exception.class`）；
5. 订单初始状态 `PENDING_PAYMENT`（待支付），15 分钟后过期（后续章节由定时任务释放库存）。

### 3.4 请求示例

```bash
curl -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -d '{
           "userId": 9001,
           "trainNo": "G1",
           "travelDate": "2026-04-15",
           "fromStation": "北京南",
           "toStation": "上海虹桥",
           "seatType": "二等座",
           "passengerName": "张三",
           "passengerIdCard": "110101200001011234"
         }'
```

### 3.5 响应示例

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "orderId": 6,
    "orderNo": "ORD202609142319531432",
    "trainNo": "G1",
    "fromStation": "北京南",
    "toStation": "上海虹桥",
    "seatType": "二等座",
    "price": 716.50,
    "status": "待支付",
    "expireAt": "2026-09-14T23:34:53.4368786"
  },
  "timestamp": 1789399193454
}
```

### 3.6 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `orderId` | long | 订单主键 |
| `orderNo` | string | 订单号，`ORD + 时间戳 + 随机数` |
| `trainNo` | string | 车次号 |
| `fromStation` / `toStation` | string | 乘车区间 |
| `seatType` | string | 席别 |
| `price` | decimal | 订单金额（区段票价之和） |
| `status` | string | 中文状态：待支付 |
| `expireAt` | datetime | 支付截止时间 |

### 3.7 错误响应

| 场景 | code | message |
| --- | --- | --- |
| 余票不足 | 2001 | 二等座 余票不足 |
| 库存扣减冲突 | 2001 | 库存扣减冲突，请重试 |
| 车次不存在/区间不符 | 1002 | 车次不存在：G9999 |
| 车站不存在 | 1001 | 出发站不存在：xxx |
| 模拟异常回滚 | 2002 | 模拟异常：扣库存与订单必须一起回滚 |
| 参数缺失 | 400 | 参数错误：userId 不能为空 |

---

## 4. 抢票接口（高并发）

### 4.1 基本信息

| 项 | 值 |
| --- | --- |
| 路径 | `POST /api/order/grab?sync=false` |
| 功能 | 高并发抢票：分布式锁串行化 + 库存原子扣减 + MQ 异步下单 |
| 实现 | `GrabController.grab` → `GrabService.grab` → `GrabQueue` → `GrabProcessor` |
| 默认模式 | `sync=false` 异步削峰；`sync=true` 同步直接返回结果 |

### 4.2 请求体

与 `POST /api/orders` 相同（`OrderCreateRequest`）：`userId`、`trainNo`、`travelDate`、`fromStation`、`toStation`、`seatType`（默认二等座）、`passengerName`、`passengerIdCard`。

### 4.3 业务规则

1. 参数校验后生成 `requestId`；异步模式先登记 QUEUED 再入队，队列满返回业务码 2003；
2. 消费者按"获取分布式锁（车次+日期+席别）→ 库存扣减（Redis Lua / 内存 CAS）→ 下单（DB 事务 / 内存订单）→ 失败回补库存 → 释放锁"执行；
3. 库存 key 为 `railway:stock:{车次}:{日期}:{席别}:{出发站}->{到达站}`，锁 key 为 `railway:lock:grab:{车次}:{日期}:{席别}`；
4. 缓存库存不是权威值，DB 层 `remaining_count >= 1` 仍是防超卖最后防线；
5. 默认内存模式（`railway.redis.mode=memory`、`railway.grab.order-store=memory`）用于无 Redis/MySQL 环境的模拟验证；生产切 `redis` + `db` 即可。

### 4.4 请求示例

```bash
# 异步抢票（推荐，MQ 削峰）
curl -X POST "http://localhost:8080/api/order/grab" \
     -H "Content-Type: application/json" \
     -d '{"userId":9001,"trainNo":"G1","travelDate":"2026-04-15","fromStation":"北京南","toStation":"上海虹桥","seatType":"二等座","passengerName":"张三"}'

# 同步抢票
curl -X POST "http://localhost:8080/api/order/grab?sync=true" \
     -H "Content-Type: application/json" \
     -d '{"userId":9001,"trainNo":"G1","travelDate":"2026-04-15","fromStation":"北京南","toStation":"上海虹桥","seatType":"二等座","passengerName":"张三"}'
```

### 4.5 响应示例

异步受理：

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "requestId": "4b4490a561ff4b59bb0860f7a2c0393d",
    "status": "QUEUED",
    "mode": "async",
    "queuePosition": 1,
    "orderNo": null,
    "price": null,
    "message": "已进入抢票队列，排队位置约 1"
  },
  "timestamp": 1789451572301
}
```

同步结果：`status` 为 `SUCCESS` 时携带 `orderNo`、`price`；失败时 `status=FAILED`、`message` 为失败原因。

### 4.6 辅助接口

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/api/order/grab/{requestId}` | 查询异步抢票结果，未知 id 返回 2004 |
| GET | `/api/order/grab/stats` | 压测统计：submitted/success/failed/rejected/orders/queueSize/queueCapacity/workerCount/firstSubmittedMillis/lastFinishedMillis/elapsedMillis/qps |
| GET | `/api/order/grab/stock?trainNo=&travelDate=&fromStation=&toStation=&seatType=` | 查询缓存库存 |
| POST | `/api/order/grab/stock/warmup` | 预热库存，body 含 `totalCount` |
| POST | `/api/order/grab/reset` | 重置内存模式数据（压测前调用） |

### 4.7 压测结果（本机内存模拟，100 并发抢 10 张票）

| 模式 | 受理 | 成功 | 失败 | 订单 | 剩余 | 提交 QPS | 平均受理耗时 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| sync | 100 | 10 | 90 | 10 | 0 | 990.1 | 1.01 ms |
| async | 100 | 10 | 90 | 10 | 0 | 1315.8 | 0.76 ms |

压测命令：`scripts/grab-stress.ps1 -Mode sync|async -Concurrency 100 -Stock 10`。

---

## 5. 换乘路径规划（第5章）

### 5.1 基本信息

| 项 | 值 |
| --- | --- |
| 路径 | `GET /api/routes/transfer` |
| 功能 | 在时间扩展图上搜索最快/最经济换乘方案 |
| 实现 | `TransferController.plan` → `TransferRouteService` → `TransferGraphProvider` → `DijkstraRoutePlanner` / `AStarRoutePlanner` |

### 5.2 请求参数（query）

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `from` | string | 是 | - | 出发站名 |
| `to` | string | 是 | - | 到达站名 |
| `strategy` | string | 否 | `fastest` | `fastest` 总历时最短 / `cheapest` 总票价最低 |
| `date` | date | 否 | 当天 | 乘车日期，DB 模式用于取区间票价 |

### 5.3 业务规则

1. `from`、`to` 必填且不能相同；
2. 换乘图节点 = 车站 + 时刻（到达/出发事件）；运行边 = 同车次乘车区段；换乘边 = 同站、不同车次、等待在 `[10, 240]` 分钟；
3. 搜索限制：最多 2 次换乘、总耗时不超过 24 小时（`railway.route.*` 可调）；
4. `fastest` 权重为历时（含换乘等待），`cheapest` 权重为区间票价（等待不计价但计入总历时）；
5. 默认 `dijkstra`，可切 `astar`（启发函数 = 剩余里程 ÷ 350km/h）；
6. 内存模式（`railway.route.mode=memory`）使用与 `sql/init.sql` 一致的样例时刻表；生产切 `db` 从 `train_station`、`seat_inventory` 装载。

### 5.4 请求示例

```bash
curl "http://localhost:8080/api/routes/transfer?from=上海虹桥&to=杭州东&strategy=fastest"
curl "http://localhost:8080/api/routes/transfer?from=上海虹桥&to=杭州东&strategy=cheapest"
```

### 5.5 响应示例

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "fromStation": "上海虹桥",
    "toStation": "杭州东",
    "strategy": "fastest",
    "strategyName": "最快",
    "algorithm": "dijkstra",
    "transferCount": 1,
    "transferStations": ["南京南"],
    "totalDurationMinutes": 348,
    "totalPrice": 360.00,
    "legs": [
      {
        "trainNo": "G2", "trainType": "G",
        "fromStation": "上海虹桥", "toStation": "南京南",
        "departureTime": "07:00", "arrivalTime": "08:23",
        "departureDayOffset": 0, "arrivalDayOffset": 0,
        "durationMinutes": 83, "price": 139.50, "waitBeforeMinutes": 0
      },
      {
        "trainNo": "G11", "trainType": "G",
        "fromStation": "南京南", "toStation": "杭州东",
        "departureTime": "11:06", "arrivalTime": "12:48",
        "departureDayOffset": 0, "arrivalDayOffset": 0,
        "durationMinutes": 102, "price": 220.50, "waitBeforeMinutes": 163
      }
    ]
  },
  "timestamp": 1789451572301
}
```

### 5.6 错误响应

| 场景 | code | message |
| --- | --- | --- |
| 缺少 from/to | 400 | 参数错误 |
| 出发到达相同 | 400 | 出发站与到达站不能相同 |
| 非法策略 | 400 | 不支持的策略：quickest，可选 fastest 或 cheapest |
| 车站不可达（不在图中） | 1001 | 出发站不存在或暂无可达车次：上海 |
| 无可行路径 | 3001 | 未找到 郑州东 到 杭州东 的可行换乘方案 |

---

## 6. 站内路径推荐（第5章）

### 6.1 基本信息

| 项 | 值 |
| --- | --- |
| 路径 | `GET /api/station/route-guide` |
| 功能 | 根据车次、编组、股道、进站方向、楼层推荐检票口、走行路线与步行时间 |
| 实现 | `StationRouteController.routeGuide` → `StationRouteService` → `StationRuleEngine`（责任链 5 条规则） |

### 6.2 请求参数（query）

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `trainNo` | string | 是 | - | 车次号，如 G1101 |
| `formationLength` | int | 是 | - | 编组长度（辆） |
| `trackNo` | string | 是 | - | 停靠股道，1-18 |
| `entryDirection` | string | 是 | - | 北/南，支持 north/south 别名 |
| `passengerFloor` | string | 是 | - | 2F/1F/3F/B1，支持 f2/f1/f3/-1f 别名 |
| `station` | string | 否 | 广州南 | 车站名 |

### 6.3 业务规则

1. 参数缺失、股道非 1-18、方向/楼层非法返回 400；
2. 责任链按固定顺序执行：股道分区（10）→ 编组长度（20）→ 进站方向（30）→ 旅客楼层（40）→ 步行核算（50）；
3. 检票口 = 区域前缀 + 股道×2 基数，按编组微调并按区域容量（A/B 16、C 8）收紧；
4. 步行时间 = 区域基础分钟 + 楼层/编组增量，截断到 `[4, 18]`；
5. 新增规则只需新增 `StationRuleHandler` Bean 并声明 `order`，核心引擎不改。

### 6.4 请求示例

```bash
curl "http://localhost:8080/api/station/route-guide?trainNo=G1101&formationLength=16&trackNo=9&entryDirection=北&passengerFloor=2F"
```

### 6.5 响应示例

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "stationName": "广州南",
    "trainNo": "G1101",
    "zone": "B",
    "recommendedGate": "B16",
    "walkingRoute": "2F北进站口→西安检区→B16检票口→9站台",
    "walkingMinutes": 6,
    "matchedRules": ["股道分区规则", "编组长度规则", "进站方向规则", "旅客楼层规则", "步行时间核算规则"],
    "notes": ["检票口已按股道容量收紧至 B16"]
  },
  "timestamp": 1789451572301
}
```

### 6.6 错误响应

| 场景 | code | message |
| --- | --- | --- |
| 缺少 trainNo | 400 | 参数错误：trainNo 为必填参数 |
| 编组长度非正 | 400 | 参数错误：formationLength 必须是正整数 |
| 股道超范围 | 400 | 参数错误：股道号仅支持 1-18：25 |
| 方向非法 | 400 | 参数错误：entryDirection 仅支持 北/南：UP |
| 楼层非法 | 400 | 参数错误：passengerFloor 仅支持 2F/1F/3F/B1：4F |

---

## 7. AI 智能购票助手（第6章）

### 7.1 基本信息

| 项 | 值 |
| --- | --- |
| 路径 | `POST /api/ai/chat` |
| 功能 | 自然语言查询车次/换乘/客运规章，Function Calling 调用后端接口 |
| 实现 | `AiChatController.chat` → `AiChatService` → `LlmGateway` → `AiToolRegistry` → 工具 |

### 7.2 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `message` | string | 是 | 用户自然语言，如 `4月15日北京到上海` |

### 7.3 业务规则

1. `message` 不能为空，否则返回 400；
2. 三个工具：`query_trains`（复用第3章查询）、`query_transfer_routes`（复用第5章时间扩展图）、`search_knowledge_base`（本地 RAG）；
3. 默认 `railway.ai.mode=mock`，用本地规则引擎模拟 Function Calling；配置 API Key 后切 `remote` 走真实大模型；
4. 主 LLM 超时/报错时按 `max-retries` 重试，仍失败自动降级 Mock，`degraded=true`；
5. `toolCalls` 返回每个工具的参数、成功标记与摘要，便于排查"模型没调工具"还是"工具报错"。

### 7.4 请求示例

```bash
curl -X POST http://localhost:8080/api/ai/chat \
     -H "Content-Type: application/json" \
     -d '{"message":"4月15日北京到上海"}'
```

### 7.5 响应示例

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "reply": "已为你查询到 1 趟车次：\n- G1 09:00-13:28，历时268分钟，二等座余3张，716.50元",
    "intent": {
      "type": "trains",
      "fromStation": "北京南",
      "toStation": "上海虹桥",
      "travelDate": "2026-04-15",
      "strategy": ""
    },
    "toolCalls": [
      {
        "name": "query_trains",
        "arguments": {"from": "北京南", "to": "上海虹桥", "date": "2026-04-15"},
        "success": true,
        "summary": "查询到 1 趟车次"
      }
    ],
    "trains": [
      {
        "trainNo": "G1", "fromStation": "北京南", "toStation": "上海虹桥",
        "departureTime": "09:00:00", "arrivalTime": "13:28:00",
        "durationMinutes": 268, "seatType": "二等座",
        "remainingCount": 3, "totalPrice": 716.50
      }
    ],
    "transferPlan": null,
    "knowledge": null,
    "degraded": true,
    "provider": "mock"
  },
  "timestamp": 1789451572301
}
```

### 7.6 知识问答（辅助接口）

| 项 | 值 |
| --- | --- |
| 路径 | `POST /api/ai/knowledge/ask` |
| 请求体 | `{"question":"退票要提前多久"}` |
| 响应 | `{question, answer, citations:[{id,title,score,snippet}], degraded}` |
| 说明 | 本地哈希向量检索，TopK=3，score 为余弦相似度加标题命中加成 |

### 7.7 错误响应

| 场景 | code | message |
| --- | --- | --- |
| message 缺失 | 400 | message 为必填参数 |
| question 缺失 | 400 | question 为必填参数 |
| 主 LLM 失败 | 0 | 自动 Mock 降级，`data.degraded=true`、`data.provider=mock`（HTTP 状态仍为 200） |

> 说明：业务成功一律 `code=0`；降级、无数据都属于“成功但需提示”的场景，通过 `data.degraded` 与空集合表达，不新增业务码。

---

## 8. 候补加开建议（第6章）

### 8.1 基本信息

| 项 | 值 |
| --- | --- |
| 路径 | `GET /api/ai/candidate-suggestion?date=2026-04-15` |
| 功能 | 按 OD/席别聚合候补，计算积压指数，超阈值生成加开建议报告 |
| 实现 | `CandidateSuggestionController` → `CandidateAnalysisService` → `CandidateBacklogRepository`（DB/内存双模式） |

### 8.2 请求参数

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `date` | date | 否 | 当天 | 乘车日期 |

### 8.3 业务规则

1. 聚合 SQL：`candidate` 按 `travel_date + from + to + seat_type` 分组统计 `WAITING`；
2. 积压指数 = 候补人数 × 席别权重（二等座1.0/硬座0.9/一等座0.6等）× 日期紧迫系数（≤3天1.3等）；
3. 指数 ≥ 阈值（默认 100）进入报告，指数 ≥150 建议 `ADD_TRAIN`，否则 `REALLOCATE_QUOTA`；
4. 报告含 30 分钟流转、120 分钟决策的广铁 SLA 时间点；
5. 定时任务每小时扫描当天并缓存结果，接口优先返回缓存。

### 8.4 响应示例（内存模式 date=2026-04-15）

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "date": "2026-04-15",
    "generatedAt": "2026-09-15T20:30:00",
    "reportDeadline": "2026-09-15T21:00:00",
    "decisionDeadline": "2026-09-15T22:30:00",
    "reportReadyMinutes": 30,
    "decisionWindowMinutes": 120,
    "threshold": 100.0,
    "scannedOdCount": 5,
    "overThresholdCount": 3,
    "summary": "2026-04-15 共扫描 5 个候补OD方向，3 个方向超过积压阈值...",
    "items": [
      {
        "fromStation": "广州南", "toStation": "武汉", "seatType": "二等座",
        "waitingCount": 157, "backlogIndex": 157.0,
        "actionType": "ADD_TRAIN", "actionName": "加开列车",
        "suggestedFormation": "16节",
        "estimatedLoadFactor": 0.707,
        "confidence": 0.82,
        "reason": "候补积压指数 157.0 超过加开阈值 150.0，建议加开 广州南→武汉 方向列车"
      }
    ]
  },
  "timestamp": 1789451572301
}
```

---

## 9. 交通接驳引导（第6章）

### 9.1 基本信息

| 项 | 值 |
| --- | --- |
| 路径 | `GET /api/travel-guide` |
| 功能 | 按车次、出发站、用户位置推荐地铁出口、进站平台、检票口与拥堵提示 |
| 实现 | `TravelGuideController` → `TravelGuideService` → 第5章 `StationRouteService` |

### 9.2 请求参数

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `trainNo` | string | 是 | - | 车次号 |
| `fromStation` | string | 是 | - | 出发站 |
| `userLocation` | string | 否 | 未提供位置 | 如 `地铁2号线`、`网约车`、`P3停车场` |

### 9.3 业务规则

1. 检票口/走行路线复用第5章责任链规则引擎，站台数据来自站台映射表；
2. 接驳映射：地铁2号线→D出口、7号线→H出口、公交→B出口、出租/网约→P1快速接客区、自驾→P3停车场，未识别→B出口；
3. 拥堵映射：07:00-09:30 与 17:00-19:30 高峰（建议提前 45 分钟），11:00-13:30 平峰，其余畅通（建议 30 分钟）。

### 9.4 响应示例

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "trainNo": "G1102",
    "fromStation": "广州南",
    "userLocation": "地铁2号线",
    "metroLine": "地铁2号线",
    "metroExit": "D 出口",
    "metroTip": "出闸后经中央扶梯上 2F 进站平台",
    "entryPlatform": "2F 西进站平台（B 区检票口）",
    "recommendedGate": "B16",
    "walkingRoute": "2F北进站口→西安检区→B16检票口→7站台",
    "walkingMinutes": 6,
    "congestionLevel": "高峰",
    "congestionTip": "早高峰，地铁出站与东安检区客流大，建议预留 45 分钟",
    "suggestedArrivalMinutes": 45,
    "generatedAt": "2026-09-15T08:00:00"
  },
  "timestamp": 1789451572301
}
```

### 9.5 错误响应

| 场景 | code | message |
| --- | --- | --- |
| trainNo 缺失 | 400 | trainNo 为必填参数 |
| fromStation 缺失 | 400 | fromStation 为必填参数 |

---

## 10. 系统与文档接口（第7-8章）

### 10.1 监控指标

| 项 | 值 |
| --- | --- |
| 路径 | `GET /internal/metrics` |
| 响应类型 | `text/plain;version=0.0.4`（Prometheus 文本格式） |
| 指标 | `railway_up`、堆/非堆内存、线程数、运行时长、GC 次数与耗时、系统负载、CPU 核数 |
| 说明 | 自实现指标端点，零新增依赖；生产可替换为 Actuator + micrometer。Nginx 对该路径返回 403，仅供内网 Prometheus 抓取 |

### 10.2 接口文档

| 路径 | 说明 |
| --- | --- |
| `GET /hello` | 骨架自检接口：返回问候字符串（`text/plain`，第0章验收用） |
| `GET /v3/api-docs` | 自实现 OpenAPI 3.0 JSON，覆盖全部 18 个接口（17 个业务接口 + `/hello`） |
| `GET /swagger-ui.html` | 离线可视化文档页（内嵌 CSS/JS，不依赖外网） |

`ApiDocCoverageTest` 用 Spring 真实映射与文档注册表对拍：新增接口未登记文档时测试失败。

### 10.3 错误响应

| 场景 | code | message |
| --- | --- | --- |
| 指标路径经 Nginx 公网访问 | HTTP 403 | Nginx 直接拒绝，不落到应用 |
| 文档路径不存在 | 404 | 正常 HTTP 语义 |

---

## 11. 事务一致性说明

下单流程的原子性由 Spring 声明式事务保证，验证方式：

1. 记录下单前的区间库存与订单数；
2. 发送带 `"failAfterDeduct": true` 的请求；
3. 再次查询，库存与订单数均不变，说明扣减 UPDATE 与订单 INSERT 一起回滚。

第3章实测：正常下单使三段库存 `320/3/210 → 319/2/209` 且订单 +1；模拟异常后库存仍为 `319/2/209`、订单数不变。

---

## 12. 变更记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v1.0 | 2026-09-14 | 第3章：新增车次查询、创建订单接口与统一响应/错误码约定 |
| v1.1 | 2026-09-15 | 第4章：新增抢票接口、结果查询、库存预热/查询/重置、压测统计与错误码 2003/2004 |
| v1.2 | 2026-09-15 | 第5章：新增换乘路径规划、站内路径推荐接口与错误码 3001 |
| v1.3 | 2026-09-15 | 第6章：新增 AI 购票助手、知识问答、候补加开建议、交通接驳引导接口 |
| v1.4 | 2026-09-15 | 第7-8章：补充监控指标、OpenAPI 文档与可视化文档接口说明，全项目接口闭环 |

全项目接口已全部实现；部署与监控的操作手册见 `docs/chapters/chapter-07.md`，收尾与验收见 `docs/chapters/chapter-08.md`。
