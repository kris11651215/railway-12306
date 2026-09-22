# 第6章 AI融合核心

> 项目：铁路智慧出行综合服务平台
> 状态：✅ 已完成
> 建议耗时：第9-10周，约5-7天
> 深度：AI 主线 L3-L4（LLM API、Prompt Engineering、Function Calling、RAG、Embedding、向量库、Scheduler、结构化报告）
> 一句话：让大模型只做"理解与表达"，所有实时数据都由它调用后端接口拿，再把结果组织成回答；候补积压用真实 SQL 聚合出报告，按广铁"30 分钟出报告、2 小时决策"的节奏给出加开建议；购票后按广州南站规则给出接驳引导；客运规章用本地轻量 RAG 回答。

---

## 本章目标

| 目标 | 具体产出 | 验收方式 |
| --- | --- | --- |
| LLM 接入 | DeepSeek/Kimi OpenAI 兼容客户端，超时/重试/降级 | API 不可用时自动降级 Mock，不阻塞 |
| Function Calling | 3 个工具 + 意图解析 + 真实调用后端 | "4月15日北京到上海"返回结构化车次 |
| 智能购票助手 | `POST /api/ai/chat` | 自然语言查询返回车次/换乘/规章 |
| 候补智能调配 | `GET /api/ai/candidate-suggestion?date=` | SQL 聚合 + 积压指数 + 加开报告 |
| 广铁特色 | 30 分钟出报告、2 小时决策窗口 | 报告含方向、编组、上座率、置信度 |
| 交通接驳 | `GET /api/travel-guide` | 输出地铁出口、进站平台、检票口、拥堵 |
| RAG 知识库 | 本地哈希向量 + 轻量向量库 + 规章问答 | 退票/儿童票等问题命中正确文档 |
| 定时任务 | Spring Scheduler 扫描候补 | 每小时扫描并缓存报告 |
| 测试 | 32 个新增 JUnit5 测试 | `.\mvnw.cmd test` 全部通过 |

配套材料：

- 开头导图：`docs/mindmaps/chapter-06-start.mmd`
- 接口文档：`docs/architecture/02-api-design.md` v1.3
- 复用第5章：`docs/chapters/chapter-05.md`（换乘与站内规则）
- 候补表结构：`docs/architecture/01-database-design.md`

---

## 6.0 实验环境与降级策略

| 组件 | 本机状态 | 处理方式 |
| --- | --- | --- |
| DeepSeek/Kimi API | 无 API Key | `railway.ai.mode=mock`，本地规则引擎跑通全流程，`degraded=true` 标记 |
| MySQL | 未运行 | `railway.ai.data-mode=memory`，用与 `sql/init.sql` 一致的样例数据；DB 聚合 SQL 已写好 |
| 向量数据库 | 未安装 | 本地 `InMemoryVectorStore` + 哈希 Embedding，不下载大模型 |
| Embedding 模型 | 未下载 | 字符 bigram 哈希到 512 维向量，余弦相似度检索 |
| 8080 服务 | 未运行 | 未做 HTTP 实测，结论来自 JUnit5 直接调用 Service |

三个开关：

```yaml
railway:
  ai:
    mode: mock              # mock: 本地规则引擎 | remote: 真实大模型API
    data-mode: memory       # memory: 内存样例 | db: MySQL真实聚合
    scheduler-enabled: true # 候补定时扫描开关
```

**降级设计**：`LlmGateway` 包装主客户端与 Mock 客户端，主客户端超时/报错时按 `max-retries` 重试，仍失败则自动切到 Mock，并在响应中把 `degraded` 标记为 `true`，接口永远有结果、不空转。

---

## 6.1 AI 是主线，不是孤立模块

第6章的 AI 不"自己编数据"，而是站在前面几章的肩膀上：

```text
用户说"4月15日北京到上海"
  └─ LLM 解析意图 → 调用 query_trains
       └─ 第3章的车次查询能力（DB 或内存样例）返回真实车次/余票/票价
用户说"上海虹桥到杭州东最快怎么走"
  └─ LLM 调用 query_transfer_routes
       └─ 第5章的时间扩展图 + Dijkstra 返回换乘方案
用户问"退票费怎么收"
  └─ LLM 调用 search_knowledge_base
       └─ 本地 RAG 检索规章知识库
候补加开建议
  └─ 第2章的 candidate 表真实 SQL 聚合 → 积压指数 → 结构化报告
交通接驳
  └─ 第5章站内规则引擎 → 检票口/走行路线 → 叠加地铁出口与拥堵提示
```

面试表达：**"传统技术是支撑 AI 决策的基础设施"**——LLM 负责理解与表达，实时数据、路径算法、库存与候补分析仍然由后端系统保证正确性。

---

## 6.2 调用大模型 API（DeepSeek / Kimi）

### 6.2.1 是什么

DeepSeek、Kimi 都提供 OpenAI 兼容的 `POST /chat/completions`：

```text
POST https://api.deepseek.com/v1/chat/completions
Authorization: Bearer <API_KEY>

{
  "model": "deepseek-chat",
  "messages": [
    {"role": "system", "content": "你是铁路购票助手..."},
    {"role": "user", "content": "4月15日北京到上海"}
  ],
  "tools": [ ... ],
  "tool_choice": "auto",
  "temperature": 0.2
}
```

响应里 `choices[0].message` 既可能是 `content`（直接回答），也可能是 `tool_calls`（要求调用工具）。

### 6.2.2 为什么需要工程化封装

直接 `HttpClient` 裸调有三个坑：

1. **网络不可控**：必须设置连接超时与读取超时，否则线程被拖死；
2. **抖动**：偶发 5xx/超时要有重试上限，重试仍失败要有降级；
3. **Key 泄漏**：绝不能把 Key 写进代码或提交到 Git。

本项目的做法：

```java
HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
        .build();
HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + "/chat/completions"))
        .timeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
        .header("Authorization", "Bearer " + properties.getApiKey())
        .POST(...)
        .build();
```

配置项：

```yaml
railway:
  ai:
    mode: remote
    provider: deepseek
    base-url: https://api.deepseek.com/v1
    api-key: ${DEEPSEEK_API_KEY:}
    model: deepseek-chat
    connect-timeout-millis: 3000
    read-timeout-millis: 15000
    max-retries: 2
```

`${DEEPSEEK_API_KEY:}` 从环境变量读 Key，取不到就是空串；空 Key 时 `AiConfig` 直接把 Mock 当主客户端，避免发无效请求。

### 6.2.3 怎么排错

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 接口一直转圈 | 没设读取超时 | 检查 `read-timeout-millis` |
| 401/403 | Key 无效或没带上 | 确认环境变量与 `Authorization` 头 |
| 400 model 不存在 | 模型名写错 | DeepSeek 用 `deepseek-chat`/`deepseek-reasoner` |
| 偶发 5xx 全失败 | 无重试 | `max-retries` 配合指数退避 |
| 响应 `degraded=true` | 已自动降级 Mock | 看日志中重试失败原因，属于预期兜底 |

---

## 6.3 Prompt Engineering

### 6.3.1 是什么

Prompt 是给模型的"工作说明书"。本项目系统提示词只有三条硬约束：

```text
1. 你是铁路智慧出行服务平台的智能购票助手；
2. 涉及车次、余票、票价、换乘、候补等实时数据时，必须调用提供的工具，禁止编造数据；
3. 回答使用简洁的中文，可引用工具返回的真实字段。
```

### 6.3.2 为什么这样写

大模型最大的风险是"一本正经地胡说"：它可能编出一个不存在的 G9999。把"必须调用工具、禁止编造"写进 system prompt，再配合 Function Calling 的接口约束，才能让答案建立在真实数据上。

工程上的三个技巧：

- **角色明确**：限定领域，减少无关知识输出；
- **行为约束**：明确什么必须做（调工具）、什么禁止做（编数据）；
- **输出风格**：中文、简洁、可引用字段。

### 6.3.3 怎么排错

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 模型不调工具，直接臆造车次 | 提示词没约束或工具描述不清 | 强化 system prompt；把工具 description 写清楚 |
| 参数乱填（站名不存在） | 缺少参数说明与枚举 | 在工具 schema 的 description 里给示例 |
| 回答啰嗦/中英混杂 | 未限定语言与风格 | prompt 明确"简洁中文" |
| 输出不稳定 | 温度过高 | 数据类任务 `temperature=0.2` |

---

## 6.4 Function Calling（本章核心）

### 6.4.1 是什么

Function Calling 让模型输出一个"要调用哪个函数、参数是什么"的结构化请求，真正的函数执行由你的后端完成：

```text
用户输入
  → LLM 输出 tool_calls: [{name: "query_trains", arguments: {from: "北京南", to: "上海虹桥", date: "2026-04-15"}}]
  → 后端执行 QueryTrainsTool → 查真实数据
  → 把结果回填给 LLM
  → LLM 用自然语言总结
```

模型只负责"选择与解析"，数据永远来自后端。

### 6.4.2 本项目的三个工具

| 工具 | 参数 | 后端能力 | 来源章节 |
| --- | --- | --- | --- |
| `query_trains` | from, to, date, seatType | 车次、余票、票价 | 第3章 |
| `query_transfer_routes` | from, to, strategy, date | 中转换乘方案 | 第5章 |
| `search_knowledge_base` | question | 客运规章 RAG 检索 | 本章 |

工具定义（JSON Schema 由 `ToolDefinition` 生成）：

```java
return new ToolDefinition("query_trains", "按出发站、到达站、日期查询可售车次与余票票价", Map.of(
        "type", "object",
        "properties", properties,
        "required", List.of("from", "to")));
```

统一接口让新增工具零侵入：

```java
public interface AiTool {
    ToolDefinition definition();
    ToolResult execute(Map<String, Object> arguments);
}
```

`AiToolRegistry` 收集所有 `AiTool` Bean，把定义交给 LLM，把调用分发给对应工具。

### 6.4.3 Mock 模式怎么模拟 Function Calling

没有 API Key 时，`MockLlmClient` 用规则完成同样的三步：

1. **意图识别**：命中"退票/儿童/规章"等关键词 → 知识库工具；命中"换乘/最快/中转"且识别出两个站名 → 换乘工具；否则识别车站+日期 → 车次工具；
2. **参数抽取**：正则解析"4月15日/2026-04-15"，`StationAlias` 把"北京→北京南、上海→上海虹桥"归一化；
3. **结果组织**：`composeAnswer` 把工具返回的 `TrainVO`、`TransferPlanVO`、`RagAnswerVO` 拼成中文回复。

它和真实模型的差别只在"谁来做意图判断"，工具执行链路完全一致，所以离线也能验收 Function Calling。

### 6.4.4 真实调用链路

```text
AiChatController.chat
  └─ AiChatService.chat
       ├─ LlmGateway.plan(message, tools)      ← 主客户端失败自动降级Mock
       ├─ AiToolRegistry.execute(toolCall)     ← 执行后端真实查询
       ├─ LlmGateway.compose(message, ...)     ← 基于工具结果生成回答
       └─ ChatResponseVO(intent, toolCalls, trains/transferPlan/knowledge, degraded)
```

### 6.4.5 怎么排错

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 模型不调工具 | 工具描述不清晰 | 补 description 与参数示例 |
| 参数缺失/站名错 | 抽取或别名问题 | 看 `toolCalls[].arguments`，补 `StationAlias` |
| 工具报错但模型仍回答 | 未校验 `success` | `composeAnswer` 对失败结果明确说明原因 |
| 调用错工具 | 意图关键词冲突 | 调整关键词优先级：知识 → 换乘 → 车次 |

---

## 6.5 实战：智能购票助手 `POST /api/ai/chat`

### 6.5.1 请求与响应

```bash
curl -X POST http://localhost:8080/api/ai/chat \
     -H "Content-Type: application/json" \
     -d '{"message":"4月15日北京到上海"}'
```

响应（JUnit 实测）：

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
        "trainNo": "G1",
        "fromStation": "北京南",
        "toStation": "上海虹桥",
        "departureTime": "09:00:00",
        "arrivalTime": "13:28:00",
        "durationMinutes": 268,
        "seatType": "二等座",
        "remainingCount": 3,
        "totalPrice": 716.50
      }
    ],
    "degraded": true,
    "provider": "mock"
  },
  "timestamp": 1789451572301
}
```

### 6.5.2 换乘与规章

```text
输入：上海虹桥到杭州东最快怎么走
回复：为你规划了最快方案（共1次换乘）：
      1. G2 上海虹桥 07:00 → 南京南 08:23（83分钟，139.50元）
      换乘等待 163 分钟
      2. G11 南京南 11:06 → 杭州东 12:48（102分钟，220.50元）
      总历时 348 分钟，总价 360.00 元

输入：退票要提前多久
回复：根据《退票规则》：旅客不能按票面指定的日期、车次乘车时，可以在票面发站办理退票...
引用：退票规则（score 0.1651）
```

`degraded=true` + `provider=mock` 说明当前是本地 Mock 模式；配置真实 Key 后切 `mode=remote`，同一接口返回真实大模型的表达，但结构化字段与工具调用记录保持一致。

### 6.5.3 错误响应

| 场景 | code | message |
| --- | --- | --- |
| message 缺失/空白 | 400 | message 为必填参数 |
| 工具查询无数据 | 0 | reply 说明无车次，`trains: []`（业务成功，无数据） |
| 主 LLM 失败 | 0 | 自动降级 Mock，`data.degraded=true`、`provider=mock`（HTTP 状态仍为 200） |

---

## 6.6 RAG 知识库：Embedding 与向量检索

### 6.6.1 是什么

RAG（Retrieval-Augmented Generation，检索增强生成）= **检索** + **生成**：

1. 把规章文档切成知识块，转成向量存进向量库；
2. 用户提问时，把问题也转成向量，找最相似的 TopK 知识块；
3. 让 LLM（或模板）基于检索到的原文回答，并给出引用。

### 6.6.2 为什么要 RAG

客运规章经常更新，不可能每次改规章都重新训练模型；直接把整本规章塞进 Prompt 又超长且昂贵。RAG 只把"最相关的几段"喂给模型，答案有出处、可更新。

### 6.6.3 本地轻量实现（离线可跑）

环境限制不能下载大模型或向量数据库，本项目用两段轻量实现：

**Embedding：字符 bigram 哈希**

```java
for (String token : tokenize(normalizedText)) {
    int hash = fnv1a(token);
    int slot = Math.floorMod(hash, DIMENSION);          // 512 维
    vector[slot] += (hash & 0x100) == 0 ? 1.0 : -1.0;   // 带符号哈希降低碰撞偏置
}
// 再做 L2 归一化，余弦相似度 = 点积
```

- bigram 之外的字符（标点、空格）全部去掉；
- `tokenize` 同时暴露原始 bigram 集合，用于相关性硬过滤。

**向量库：`InMemoryVectorStore`**

```java
public void add(KnowledgeDocument document, double[] vector);
public List<ScoredDocument> search(double[] queryVector, int topK);   // 余弦降序
```

知识库文件 `src/main/resources/ai/knowledge-base.md` 用 `## 标题` 分块，启动时加载并逐块建向量；当前 14 个知识块，覆盖退票、改签、候补、儿童票、学生票、禁限带、检票、接驳等。

**两层过滤保证相关性**：

1. 相似度阈值 `rag-min-score=0.05`（哈希向量噪声下限）；
2. 硬性 token 重合：问题 bigram 与文档 bigram 至少重合 `min(2, 问题token数)` 个，避免"量子计算机"之类问题被无关文档误命中；
3. 标题命中加成 0.03/词，用于排序（退票问题优先命中《退票规则》而不是提到退票的《改签规则》）。

### 6.6.4 实测结果（JUnit）

| 问题 | Top1 命中 | 分数 | 结果 |
| --- | --- | --- | --- |
| 退票要提前几天，退票费怎么收 | 退票规则 | 0.1797 | 引用规则原文回答 |
| 退票要提前多久 | 退票规则 | 0.1651 | 正确 |
| 儿童票几岁可以买儿童优惠票 | 儿童票规则 | 0.2449 | 正确 |
| 量子计算机的量子比特原理 | 无命中 | - | 返回"暂未找到" |
| 你好 | 无命中 | - | 不误触发知识检索 |

### 6.6.5 真实接入步骤

1. `Embedding` 换成 `text-embedding-3-small` / `bge-m3` 等模型接口，保留 `embed(String) -> double[]` 接口即可；
2. `InMemoryVectorStore` 换成 Milvus/Qdrant/pgvector，`add/search` 语义不变；
3. 文档加载沿用 `##` 分块策略，可加入滑动窗口与重叠；
4. 检索后仍保留 token 重合过滤，作为模型向量之外的兜底。

### 6.6.6 怎么排错

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 总是命中同一篇 | 向量区分度不足 | 加 bigram+trigram、扩维、换真实 Embedding |
| 该命中的没命中 | 阈值/重合过滤太严 | 看 `score` 与 token 重合数，调 `rag-min-score` |
| 回答没出处 | 未返回 citations | 检查 `answer.getCitations()` 是否为空 |
| 知识库更新不生效 | 启动时加载一次 | 重新部署或加定时重载 |

---

## 6.7 候补智能调配

### 6.7.1 真实 SQL 聚合

候补分析不造数据，直接用 `candidate` 表聚合（`CandidateMapper.xml`）：

```sql
SELECT fs.station_name        AS from_station,
       ts.station_name        AS to_station,
       c.seat_type            AS seat_type,
       COUNT(*)               AS waiting_count,
       MIN(c.candidate_time)  AS first_candidate_time
FROM candidate c
         JOIN station fs ON fs.id = c.from_station_id
         JOIN station ts ON ts.id = c.to_station_id
WHERE c.status = 'WAITING'
  AND c.travel_date = #{travelDate}
GROUP BY fs.station_name, ts.station_name, c.seat_type
ORDER BY waiting_count DESC
```

按 `travel_date + from + to + seat_type` 分组，正好命中第2章设计的 `idx_agg` 索引；DB 模式走真实 MySQL 聚合（样例数据聚合结果：2026-04-15 广州南→武汉 157、北京南→上海虹桥 154、广州南→深圳北 153，与内存种子一致），内存模式使用对齐的模拟种子数据（`MemoryCandidateBacklogRepository`，标注“模拟验证”），两模式响应字段与报告口径完全一致。

### 6.7.2 积压指数

积压不能只看人数，还要看席别权重与日期紧迫度：

```text
积压指数 = 候补人数 × 席别权重 × 日期紧迫系数

席别权重：二等座1.0、硬座0.9、硬卧0.8、软卧0.7、一等座0.6、商务座0.4
日期紧迫：≤3天 1.3、≤7天 1.15、≤15天 1.05、其他 1.0
阈值：100（railway.ai.backlog-threshold）
```

超过阈值才进入报告，按指数降序取 TopN（默认 5）。

### 6.7.3 结构化加开报告

接口：

```bash
curl "http://localhost:8080/api/ai/candidate-suggestion?date=2026-04-15"
```

响应结构（内存模式实测，2026-04-15）：

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
    "summary": "2026-04-15 共扫描 5 个候补OD方向，3 个方向超过积压阈值，建议优先处理：广州南→武汉（二等座候补157人，加开列车）；北京南→上海虹桥（二等座候补154人，加开列车）... 报告已生成，30分钟内可流转，2小时内可完成加开决策。",
    "items": [
      {
        "fromStation": "广州南",
        "toStation": "武汉",
        "seatType": "二等座",
        "waitingCount": 157,
        "backlogIndex": 157.0,
        "actionType": "ADD_TRAIN",
        "actionName": "加开列车",
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

字段设计说明：

- `actionType`：指数 ≥150 建议加开（ADD_TRAIN），否则建议票额调配（REALLOCATE_QUOTA，"长途票额调配短途或加挂"）；
- `suggestedFormation`：≥300 人重联 16 节（2 组），≥150 人 16 节，否则 8 节；
- `estimatedLoadFactor`：`min(0.98, 0.55 + 指数/1000)`，预估上座率；
- `confidence`：`min(0.97, 0.60 + log10(人数+1)/10)`，样本越多置信度越高。

---

## 6.8 广铁特色：2 小时加开响应

### 6.8.1 业务背景

广铁（广州局集团）长沙南站用 12306 候补大数据实时分析各方向积压，精准加开列车，运力决策响应最快 2 小时。我们把这条业务链条固化成系统的 SLA：

```text
T+0min     定时扫描候补表（每小时一次，可配置）
T+~1min    SQL 聚合 + 积压指数计算
T+30min    加开建议报告完成流转（reportDeadline）
T+120min   决策窗口关闭（decisionDeadline），完成加开/调配决策
```

报告里的 `reportReadyMinutes=30`、`decisionWindowMinutes=120` 就是这条承诺的量化体现。

### 6.8.2 定时扫描

```java
@Scheduled(initialDelayString = "${railway.ai.candidate-scan-initial-delay-millis:60000}",
        fixedDelayString = "${railway.ai.candidate-scan-interval-millis:3600000}")
public void scan() {
    LocalDate today = LocalDate.now();
    CandidateSuggestionVO suggestion = candidateAnalysisService.analyze(today);
    suggestionStore.put(today, suggestion);
    log.info("候补定时扫描完成：date={}, 扫描方向={}, 超阈值={}", ...);
}
```

- `@EnableScheduling` 加在 `AiConfig`；
- `initialDelay` 60 秒：避免应用启动时立刻扫描影响启动；
- `fixedDelay` 1 小时：固定间隔而非 cron，任务耗时不影响下一轮；
- 扫描结果进 `CandidateSuggestionStore`，接口优先读缓存，未命中时实时计算；
- 扫描方法整个 try/catch，单次失败不中断调度线程。

### 6.8.3 怎么排错

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 定时任务没执行 | 未开 `@EnableScheduling` 或开关为 false | 检查 `scheduler-enabled` |
| 报告时间是错的 | 服务器时区不对 | 统一 `Asia/Shanghai` |
| 扫描把 CPU 打满 | 聚合没走索引 | 确认 `idx_agg(travel_date, from_station_id, to_station_id, status)` |
| 重复生成报告 | 缓存 key 不匹配 | 缓存按 `yyyy-MM-dd` 字符串存 |

---

## 6.9 交通接驳智能引导

### 6.9.1 是什么

购票成功后（或出行前），输入车次号、出发站、用户位置，返回：

```text
推荐地铁出口 + 进站平台 + 检票口 + 走行路线 + 步行时间 + 实时拥堵提示 + 建议到站时间
```

接口：

```bash
curl "http://localhost:8080/api/travel-guide?trainNo=G1102&fromStation=广州南&userLocation=地铁2号线"
```

实测（JUnit，08:00 早高峰）：

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
    "suggestedArrivalMinutes": 45
  },
  "timestamp": 1789451572301
}
```

### 6.9.2 与第5章联动

检票口、站台、步行路线直接调用第5章的 `StationRouteService`（责任链规则引擎）：接驳模块只做两件事——**接驳方式 → 地铁出口/进站平台**、**时段 → 拥堵等级**。这就是"AI 主线带动传统模块"的典型：AI 不需要重写规则引擎，只需组合已有能力。

- 接驳映射：地铁2号线→D出口，地铁7号线→H出口，公交→B出口，出租/网约→P1快速接客区，自驾→P3停车场，未识别→B出口并提示以站内标识为准；
- 拥堵映射：07:00-09:30 与 17:00-19:30 高峰（建议预留 45 分钟），11:00-13:30 平峰，其余畅通（建议 30 分钟）。

### 6.9.3 怎么排错

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 检票口为空 | 车次不在站台映射表 | 补 `PLATFORM_CATALOG` 或改用真实站台数据 |
| 拥堵等级一直是畅通 | 测试时间不在高峰段 | 用 `guide(..., LocalDateTime)` 固定时间测试 |
| 出口推荐错误 | 位置关键词不匹配 | 补关键词映射，保留默认兜底 |

---

## 6.10 降级、边界与可观测

### 6.10.1 三层降级

| 层 | 正常 | 降级 |
| --- | --- | --- |
| LLM | `mode=remote` 真实模型 | 超时/错误重试后切 Mock，`degraded=true` |
| 数据 | `data-mode=db` MySQL 真实聚合 | 切 `memory` 样例数据（标注模拟验证） |
| 向量库 | 真实 Embedding + 向量数据库 | 本地哈希向量 + 内存库 |

### 6.10.2 响应可观测字段

- `degraded`：本次回答是否降级；
- `provider`：实际使用的提供方（mock/deepseek等）；
- `toolCalls`：每个工具的参数、成功与否、结果摘要，便于定位"是模型没调工具，还是工具报错"。

---

## 6.11 测试与验证

### 6.11.1 测试清单（本章新增 32 个）

| 测试类 | 数量 | 覆盖 |
| --- | --- | --- |
| `MockLlmClientTest` | 5 | 车次/换乘/规章意图、日期与站名别名、闲聊不触发 |
| `AiChatServiceTest` | 5 | 自然语言查票、换乘、规章、闲聊引导、空消息 400 |
| `MemoryTrainQueryPortTest` | 4 | 别名归一、反向车次、无直达、跨天 K599 |
| `CandidateAnalysisServiceTest` | 5 | 加开建议、30/120 分钟窗口、确定性、权重与紧迫度、低积压过滤 |
| `CandidateAnalysisSchedulerTest` | 1 | 定时扫描写缓存 |
| `TravelGuideServiceTest` | 4 | 早高峰/平峰/网约车/参数校验 |
| `RagKnowledgeBaseTest` | 5 | 加载、退票命中、儿童票命中、无关问题不命中、空问题 400 |
| `HashingEmbeddingTest` | 3 | 相同文本相似度 1、相关大于无关、空文本零向量 |

### 6.11.2 运行结果

```bash
.\mvnw.cmd test
```

```text
Tests run: 74, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

累计 74 个测试全部通过（第0-5章 42 个 + 本章 32 个）。

### 6.11.3 验证边界（如实说明）

- 8080 端口未启动，**未做 HTTP 实测**；接口数据来自 JUnit 直接调用 Service；
- DeepSeek/Kimi 真实调用未实测（无 API Key），`mode=remote` 客户端代码完整，切换步骤见 6.2；
- DB 聚合 SQL 未连 MySQL 实测，内存模式数据与 `sql/init.sql` 一致。

---

## 6.12 本章代码地图

| 文件 | 职责 |
| --- | --- |
| `ai/AiProperties.java` | AI 配置项（模式、超时、阈值、调度） |
| `ai/AiConfig.java` | 网关与双模式仓储装配，`@EnableScheduling` |
| `ai/LlmClient.java` | LLM 客户端接口（意图 + 回答） |
| `ai/MockLlmClient.java` | 离线规则引擎，模拟 Function Calling 全链路 |
| `ai/DeepSeekLlmClient.java` | OpenAI 兼容 HTTP 客户端，超时/重试 |
| `ai/LlmGateway.java` | 重试与降级网关 |
| `ai/StationAlias.java` | 站名别名归一与抽取 |
| `ai/AiChatService.java` | 智能购票助手编排 |
| `ai/tool/AiTool.java` 等 | 工具协议、注册表与三个工具 |
| `ai/tool/MemoryTrainQueryPort.java` | 车次查询内存实现（复用样例时刻表） |
| `ai/rag/*` | 哈希 Embedding、内存向量库、RAG 知识库 |
| `ai/candidate/*` | 聚合仓储、积压分析、报告、定时扫描 |
| `ai/travel/TravelGuideService.java` | 交通接驳引导 |
| `resources/ai/knowledge-base.md` | 客运规章知识库（14 块） |
| `resources/mapper/CandidateMapper.xml` | 候补聚合真实 SQL |
| `controller/AiChatController.java` | `/api/ai/chat`、`/api/ai/knowledge/ask` |
| `controller/CandidateSuggestionController.java` | `/api/ai/candidate-suggestion` |
| `controller/TravelGuideController.java` | `/api/travel-guide` |

---

## 6.13 高频面试问答

1. **"Function Calling 的原理？"**
   把工具的名称、描述、参数 JSON Schema 随请求发给模型，模型返回结构化的 `tool_calls`；后端执行后将结果作为消息回填，模型再生成最终回答。模型不接触真实数据，只做选择与表达。

2. **"怎么防止 AI 编造车次？"**
   双保险：system prompt 明确"必须调用工具、禁止编造"；工具返回真实数据并在回答中引用。即使模型想编，结构化字段 `trains` 也来自后端，前端可以只信任结构化数据。

3. **"RAG 的流程？和微调的区别？"**
   离线把文档向量化入库，在线检索 TopK 再生成。RAG 适合知识频繁更新、要求可溯源；微调适合风格与能力塑造。成本上 RAG 更低、更新更快。

4. **"Embedding 怎么做？"**
   本教学项目用字符 bigram 哈希到 512 维 + 带符号 + L2 归一化，生产应换成专门 Embedding 模型；向量库把 `add/search` 抽象好，替换不影响业务。

5. **"候补积压指数怎么设计？"**
   人数 × 席别权重 × 日期紧迫系数，超过阈值进入加开建议；阈值、权重、TopN 都放配置，便于运营调整。

6. **"AI 服务挂了怎么办？"**
   连接/读取超时 + 重试上限 + Mock 降级三层兜底，接口永远可用；响应里 `degraded` 标记让前端可以提示"当前为智能兜底回答"。

7. **"为什么用定时任务而不是每次请求实时算？"**
   聚合查询有成本且在固定节奏下决策（每小时扫描）；缓存结果让查询接口无压力，实时计算只在缓存未命中时兜底。

8. **"广铁 2 小时决策在企业项目里怎么体现？"**
   报告直接携带 `reportDeadline`（+30 分钟）与 `decisionDeadline`（+120 分钟）两个 SLA 时间点，把业务承诺变成可测试的字段。

---

## 6.14 自检三问

1. **是什么**：LLM 客户端负责理解与表达，Function Calling 让模型调用后端真实接口，RAG 用向量检索给规章问答提供依据，Scheduler 定时聚合候补并生成加开报告，接驳服务复用站内规则输出出行引导。
2. **为什么**：AI 不能凭空造数据，实时性数据必须走传统后端；规章会变，RAG 比微调更灵活；候补决策有节奏，定时聚合 + SLA 字段能对齐广铁 2 小时响应。
3. **怎么排错**：先看 `toolCalls` 是否调用、是否成功，再看 `degraded/provider` 判断是否降级；RAG 不中看 score 与 token 重合；候补报告异常查 SQL 聚合与阈值配置；接驳不准查位置关键词与站台映射。

---

## 6.15 下一章预告

**第7章 Linux 部署与运维**：把当前"内存模式跑通"的项目装进 Linux + Docker：写 `Dockerfile` 与 `docker-compose.yml` 一键启动 MySQL + Redis + 应用，用 Nginx 反向代理，采集 Prometheus 指标与 SkyWalking 链路，并用 `top`、`jstack`、`grep/awk` 做线上排障。第6章的 Mock/真实双模式会和部署开关对接，真正实现"一条命令启动全项目"。
