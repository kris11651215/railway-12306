# 铁路智慧出行综合服务平台

> 一个以 AI 为主线的铁路客运后端项目：余票查询、高并发抢票防超卖、中转换乘规划、广州南站站内引导、AI 智能购票助手与候补加开建议。
> 技术主线：Java 17 + Spring Boot 3 + MySQL + Redis + 自研时间扩展图算法 + LLM Function Calling / RAG。
> 学习与面试项目，全部结论可离线复现（`.\mvnw.cmd test`，90 个测试）。

---

## 一、三句话看懂业务痛点

1. **余票查询慢**：一个座位在 A—B—C—D 行程中可裂变成多段售卖，余票不是"总数减已售"，而是区间内每个原子区段的最小值，算不准就算不快。
2. **抢票易超卖**：高峰期大量请求同时扣减多条区段库存，只靠数据库行锁会顶不住，必须缓存预扣、分布式锁、原子脚本与削峰队列配合。
3. **换乘与候补不聪明**：无直达车时找不到合理换乘；候补积压了却看不到"该加开哪个方向、加几节编组"的决策依据。

项目把这三件事分别落到：席位复用存储 + Redis 分布式锁抢票 + 时间扩展图换乘 + 候补积压指数加开报告。

---

## 二、项目缘起

我是一名华东交通大学的学生，学校原隶属铁道部，被称为"铁路系统黄埔军校"。校友单杏花师姐是 12306 客票系统的技术带头人，她的工作让我看到技术能实实在在支撑亿万旅客的出行。

本项目受此启发，从 12306 真实痛点切入：以 AI 为主线，以通用后端能力为基本盘，结合广铁数智化实践做深度定制。完整故事见 [`docs/project-origin.md`](docs/project-origin.md)。

---

## 三、核心亮点（STAR 法则）

### 亮点 1：高并发抢票零超卖

- **S**：100 并发抢 10 张票，必须恰好生成 10 个订单，不多不少。
- **T**：把数据库事务防超卖升级为"分布式锁 + 原子扣减 + 异步削峰"。
- **A**：`DistributedLockManager`（Redis/memory 双实现）+ `StockStore`（Redis Lua/内存 CAS）+ `GrabQueue` 内存 MQ + 4 消费者；DB 层保留 `remaining_count >= 1` 最后防线。
- **R**：100 并发抢 10 票 → **10 个订单、0 超卖**；提交 QPS **990.1（sync）/ 1315.8（async）**，平均受理 **1.01ms / 0.76ms**（内存模拟验证，见 [`docs/chapters/chapter-04.md`](docs/chapters/chapter-04.md) 与 [`docs/architecture/02-api-design.md`](docs/architecture/02-api-design.md)）。

### 亮点 2：时间扩展图换乘规划

- **S**：无直达车时要给出合理换乘，不能出现"上海→北京→苏州"式绕路。
- **T**：把"车站+时刻"建模成时间扩展图，支持最快/最经济双策略。
- **A**：事件节点（到达/出发）+ 运行边 + 换乘边（10 分钟 ≤ 等待 ≤ 240 分钟）；多源 Dijkstra，A* 用"剩余里程 ÷ 350km/h"启发；换乘次数 ≤2、总耗时 ≤24h 剪枝。
- **R**：无直达时上海虹桥→杭州东自动给出 **G2 07:00→08:23 + G11 11:06→12:48，全程 348 分钟、360 元**；换乘站恰为南京南；A* 与 Dijkstra 结果一致（测试断言）。

### 亮点 3：AI 主线（不编数据）

- **S**：用户说"4月15日北京到上海"，系统要返回真实车次；问"退票要提前多久"要给出规章依据。
- **T**：LLM 只做理解与表达，实时数据一律走 Function Calling 调后端。
- **A**：3 个工具（车次查询/换乘规划/规章检索）+ 本地 RAG（512 维哈希 Embedding + 内存向量库 + 14 块客运规章）+ 超时重试与 Mock 降级；无 API Key 也能离线跑通全链路，响应标记 `degraded/provider`。
- **R**：自然语言查票返回结构化 G1（余 3 张、716.50 元）；RAG 命中《退票规则》。

### 亮点 4：广铁特色（候补 2 小时响应 + 站内接驳）

- **S**：广铁长沙南用候补大数据驱动动态加开，运力决策最快 2 小时；广州南站接驳信息分散。
- **T**：把业务 SLA 与站内规则做成可测试的接口。
- **A**：候选表真实 SQL 聚合 → 积压指数（人数 × 席别权重 × 日期紧迫度）→ 结构化加开报告（方向/编组/上座率/置信度/30 分钟出报告/120 分钟决策）；接驳接口复用站内责任链规则引擎输出地铁出口、进站平台、检票口与拥堵提示。
- **R**：广州南→武汉候补 157 人 → 报告"建议加开 广州南→武汉 方向列车，16 节编组，预估上座率 70.7%，置信度 0.82"；接驳推荐"地铁 2 号线 D 出口 + B16 检票口 + 早高峰预留 45 分钟"。

---

## 四、技术栈

| 层次 | 技术 | 说明 |
| --- | --- | --- |
| 语言/框架 | Java 17、Spring Boot 3.5、Spring MVC | REST + 构造函数注入 |
| 数据访问 | MyBatis 3、MySQL 8.4 | XML 动态 SQL、ResultMap、`FOR UPDATE` 事务 |
| 缓存/并发 | Redis 7、Lua、内存双模式 | 分布式锁、库存预扣、抢票削峰 |
| 算法 | 时间扩展图、Dijkstra、A*、责任链/策略模式 | 换乘规划、站内规则引擎 |
| AI | LLM（DeepSeek/Kimi OpenAI 兼容）、Function Calling、RAG、哈希 Embedding | Mock/remote 双模式、超时重试降级 |
| 测试 | JUnit 5、Mockito、并发测试 | 90 个测试，离线全通过 |
| 部署 | Docker 多阶段构建、Docker Compose、Nginx、Prometheus、Grafana、SkyWalking（方案） | 4 核心服务 + 3 可选 profile |
| 文档 | Markdown、离线打印 HTML、Mermaid 源码 | 章节材料 + 总册合并 |

---

## 五、量化指标（真实可复现）

| 指标 | 实测值 | 来源 |
| --- | --- | --- |
| 抢票并发 | 100 并发抢 10 票 → 10 订单、0 超卖 | 第4章并发测试/压测 |
| 提交 QPS | 990.1（sync）/ 1315.8（async） | 第4章压测（内存模拟） |
| 平均受理耗时 | 1.01ms / 0.76ms | 第4章压测 |
| 索引优化 | 候补聚合 `type=ALL → ref`，扫描行数 3001 → 1018 | 第2章 EXPLAIN 实测 |
| 覆盖索引 | `key_len=20`、`Using index` 不回表 | 第2章 EXPLAIN 实测 |
| 换乘规划 | 上海虹桥→杭州东 2 程 348 分钟 / 360 元，换乘站南京南 | 第5章单测 |
| 规则引擎 | 4 组真实输入推荐正确，新增规则核心零改动 | 第5章单测 |
| AI 意图 | "4月15日北京到上海"→结构化 G1 | 第6章单测 |
| RAG | 退票/儿童票问题命中对应规章，无关问题不误命中 | 第6章单测 |
| 候补报告 | 157 人 → 加开建议（16 节 / 上座率 0.707 / 置信度 0.82） | 第6章单测 |
| 自动化测试 | 90 个测试全部通过（`.\mvnw.cmd test`） | 本仓库 |

> 说明：QPS 为内存模式下的模拟验证数据；未连接真实 Redis/MySQL/JMeter 的性能数据不作为结论。所有"模拟验证"均在文档中标注。

---

## 六、功能与接口

| 模块 | 接口 | 说明 |
| --- | --- | --- |
| 车次查询 | `GET /api/trains` | O-D + 日期查余票票价 |
| 下单 | `POST /api/orders` | 事务扣库存、模拟回滚开关 |
| 抢票 | `POST /api/order/grab` | 同步/异步、结果查询、库存预热与统计 |
| 换乘规划 | `GET /api/routes/transfer` | fastest / cheapest |
| 站内引导 | `GET /api/station/route-guide` | 广州南检票口与走行路线 |
| 智能助手 | `POST /api/ai/chat` | Function Calling 查车次/换乘/规章 |
| 知识问答 | `POST /api/ai/knowledge/ask` | 本地 RAG |
| 候选加开 | `GET /api/ai/candidate-suggestion` | 积压指数与加开报告 |
| 接驳引导 | `GET /api/travel-guide` | 地铁出口/平台/检票口/拥堵 |
| 运维监控 | `GET /internal/metrics` | Prometheus 文本指标 |

API 文档：

- 离线可视化文档（自实现 OpenAPI 3.0）：`GET /swagger-ui.html`（JSON：`GET /v3/api-docs`）
- 完整字段说明：[`docs/architecture/02-api-design.md`](docs/architecture/02-api-design.md)
- 算法设计：[`docs/architecture/03-algorithm-design.md`](docs/architecture/03-algorithm-design.md)
- 数据库设计：[`docs/architecture/01-database-design.md`](docs/architecture/01-database-design.md)

> 生产接入 springdoc/knife4j 的步骤见 [`docs/chapters/chapter-08.md`](docs/chapters/chapter-08.md)。

---

## 七、快速开始

### 方式一：离线模式（无需 MySQL、Redis、API Key）

```bash
.\mvnw.cmd test              # 90 个测试，全部离线
.\mvnw.cmd spring-boot:run   # 内存模式启动（启动前请确认在外部终端手动执行）
```

默认 `railway.redis.mode=memory`、`railway.grab.order-store=memory`、`railway.route.mode=memory`、`railway.ai.mode=mock`，所有能力都能跑通。

### 方式二：Docker 一键部署

```bash
docker compose up -d                     # MySQL + Redis + 应用 + Nginx
docker compose up -d --profile monitoring   # 追加 Prometheus + Grafana
docker compose up -d --profile tracing      # 追加 SkyWalking（链路追踪方案）
docker compose up -d --scale app=2          # 应用扩容，Nginx 动态负载均衡
```

> 样例数据集中在 2026-04-15 ~ 2026-05-16，演示换乘/抢票时请带 `date=2026-04-15`。

编排文件：[`docker-compose.yml`](docker-compose.yml)；镜像构建：[`Dockerfile`](Dockerfile)；Nginx 配置：[`deploy/nginx/nginx.conf`](deploy/nginx/nginx.conf)；部署与排障手册：[`docs/chapters/chapter-07.md`](docs/chapters/chapter-07.md)。

### 方式三：接入真实大模型

```bash
set DEEPSEEK_API_KEY=sk-xxxx
# application.yml: railway.ai.mode=remote
```

超时、重试与 Mock 降级策略见 [`docs/chapters/chapter-06.md`](docs/chapters/chapter-06.md)。

---

## 八、项目结构

```text
railway-12306/
├── Dockerfile / docker-compose.yml / .dockerignore
├── deploy/                  # Nginx、Prometheus、Grafana、SkyWalking 配置
├── docs/
│   ├── chapters/            # 第0-8章学习材料
│   ├── mindmaps/            # 开头/结尾导图与总进度（.mmd）
│   ├── print/               # 打印版 HTML 与 full-book.html
│   ├── architecture/        # 数据库/API/算法设计
│   ├── domain/              # 铁路业务常识
│   └── project-origin.md    # 项目缘起
├── scripts/                 # md2print.mjs、merge-print.mjs、grab-stress.ps1
├── sql/init.sql             # 建表与样例数据
└── src/
    ├── main/java/com/railway/
    │   ├── algorithm/       # 时间扩展图、Dijkstra/A*、样例时刻表
    │   ├── ai/              # LLM、Function Calling、RAG、候补分析、接驳
    │   ├── apidoc/          # OpenAPI 文档注册表
    │   ├── controller/ service/ mapper/ entity/ dto/ config/ ruleengine/
    │   └── common/ exception/
    └── test/java/com/railway/   # 90 个测试
```

---

## 九、学习材料导航

| 内容 | 链接 |
| --- | --- |
| 总起导图 | [`docs/mindmaps/00-overall.md`](docs/mindmaps/00-overall.md) |
| 总进度导图 | [`docs/mindmaps/00-progress.md`](docs/mindmaps/00-progress.md) |
| 项目总复盘导图 | [`docs/mindmaps/99-final.md`](docs/mindmaps/99-final.md) |
| 第0章 序章与环境 | [`docs/chapters/chapter-00.md`](docs/chapters/chapter-00.md) |
| 第1章 Java核心建模 | [`docs/chapters/chapter-01.md`](docs/chapters/chapter-01.md) |
| 第2章 MySQL专题 | [`docs/chapters/chapter-02.md`](docs/chapters/chapter-02.md) |
| 第3章 MyBatis整合 | [`docs/chapters/chapter-03.md`](docs/chapters/chapter-03.md) |
| 第4章 高并发与Redis | [`docs/chapters/chapter-04.md`](docs/chapters/chapter-04.md) |
| 第5章 路径规划与规则引擎 | [`docs/chapters/chapter-05.md`](docs/chapters/chapter-05.md) |
| 第6章 AI融合核心 | [`docs/chapters/chapter-06.md`](docs/chapters/chapter-06.md) |
| 第7章 Linux部署与运维 | [`docs/chapters/chapter-07.md`](docs/chapters/chapter-07.md) |
| 第8章 项目收尾与包装 | [`docs/chapters/chapter-08.md`](docs/chapters/chapter-08.md) |
| 打印总册 | [`docs/print/full-book.html`](docs/print/full-book.html) |

---

## 十、验证边界（如实说明）

- 本机未安装 Docker/Nginx/Prometheus/Grafana/SkyWalking，部署编排为**配置静态校验 + 可复现手册**，未实机拉起；
- 本机未运行 MySQL/Redis，DB 与 Redis 路径代码完整，默认以内存实现完成 90 个离线测试；
- 未配置真实 LLM API Key，`mode=mock` 跑通 Function Calling 全链路，`mode=remote` 代码就绪；
- HTTP 接口在未启动服务的环境下未做在线验证，接口数据来自 JUnit 直接调用 Service（同一调用链）。

面试或演示前，按 [`docs/chapters/chapter-07.md`](docs/chapters/chapter-07.md) 的"验收复现步骤"在装有 Docker 的机器上一条命令即可拉起全项目。

---

## 十一、面试故事线（一页版）

1. **业务理解**：从席位复用、票额分配、递远递减讲 12306 的真实复杂度，而不是"我写了个 CRUD"。
2. **工程能力**：MySQL 索引优化 → MyBatis 动态 SQL → 事务防超卖 → Redis 锁 + Lua + 削峰 → 时间扩展图 → AI Function Calling/RAG → Docker 部署监控，一条技术演进线讲清楚"为什么每一步是下一步的前提"。
3. **广铁契合**：候补大数据驱动动态加开（2 小时决策 SLA）、广州南站 500 条规则简化版与接驳引导，直接对应广铁数智化实践。
4. **华交底色**：以单杏花校友与 12306 精神谱系作为项目动机，用技术致敬铁路。

---

## 十二、声明

本项目为个人学习与求职作品，业务数据为教学样例（含模拟验证标注），非官方系统；涉及规章内容为公开常识整理，真实出行请以 12306 官方公告为准。
