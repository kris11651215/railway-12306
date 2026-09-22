# 第8章 项目收尾与包装

> 项目：铁路智慧出行综合服务平台
> 状态：✅ 已完成
> 建议耗时：第11-12周，约3-5天
> 深度：Swagger/Knife4j L2（会用、能配）；JUnit5+Mockito L3（能写能排错）；Git/README/STAR 为工程素养
> 一句话：给全部接口配可访问的文档，用 JUnit5 + Mockito 守住核心 Service，写一份面试官 3 分钟看懂、量化指标真实的 README，并把八章材料合并成一本可打印的总册。

---

## 本章目标

| 目标 | 具体产出 | 验收方式 |
| --- | --- | --- |
| 接口文档 | `/swagger-ui.html` + `/v3/api-docs`（OpenAPI 3.0） | 页面可访问、接口全覆盖（覆盖率测试） |
| 测试体系 | Mockito 单测 + 已有并发/AI 测试 | `.\mvnw.cmd test` 全部通过 |
| README | 痛点 + 技术栈 + STAR 量化 + 华交叙事 + 部署/API 链接 | 面试官 3 分钟能看懂 |
| 打印总册 | `docs/print/full-book.html` | 封面/目录/导图/各章/结尾导图齐全 |
| 总复盘 | `docs/mindmaps/99-final.md` | 能看出知识网络而非章节罗列 |
| Git 规范 | `feat(章节号): 描述` 的提交习惯 | 提交历史可读 |

配套材料：

- 开头导图：`docs/mindmaps/chapter-08-start.mmd`
- 复盘导图：`docs/mindmaps/chapter-08-end.mmd`
- 总复盘：`docs/mindmaps/99-final.md`
- 打印总册：`docs/print/full-book.html`
- README：`README.md`

---

## 8.0 环境与验证边界

| 项 | 本机状态 | 处理方式 |
| --- | --- | --- |
| springdoc / Knife4j | 本地仓库无依赖 | 不下载；用**自实现 OpenAPI 3.0 文档**（`/v3/api-docs` + `/swagger-ui.html`），并在 8.1.4 给出生产替换步骤 |
| Testcontainers | 仅 BOM、无核心包且无 Docker | 不下载不安装；给出集成测试代码模板与接入步骤，标注"未实机" |
| Mockito | 已随 `spring-boot-starter-test` 提供 | 直接用于核心 Service 单测 |
| Pandoc / LaTeX / mmdc | 未安装 | full-book 用自写 Node 脚本离线合并；PDF 只给命令不执行 |

---

## 8.1 Swagger / Knife4j：让接口可被看见

### 8.1.1 是什么

- **OpenAPI 3.0**：描述 REST 接口的规范（路径、方法、参数、请求体、响应）。
- **Swagger UI**：把 OpenAPI 文档渲染成可视化页面，支持"试用"。
- **Knife4j**：基于 springdoc 的增强 UI，国内团队常用，支持文档分组、离线文档导出。

标准接入（有网络环境的项目）：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

```java
@Bean
public OpenAPI railwayOpenApi() {
    return new OpenAPI().info(new Info()
            .title("铁路智慧出行综合服务平台 API")
            .version("1.0.0"));
}
```

访问 `http://localhost:8080/swagger-ui/index.html`，Knife4j 则是 `/doc.html`。

### 8.1.2 为什么本章要自实现

离线约束下不能下载新依赖，但"接口必须有文档"这条验收不能打折。于是用零依赖方式实现同样的能力：

```text
config/ApiDocConfig.java      定义 18 个接口的元数据（分组/摘要/参数/请求体）
apidoc/ApiDocRegistry.java    生成标准 OpenAPI 3.0 JSON
controller/ApiDocController.java
  GET /v3/api-docs            OpenAPI JSON
  GET /swagger-ui.html        自渲染可视化页面（内嵌 CSS/JS，不依赖外网）
```

### 8.1.3 用测试保证"文档不虚"

文档最容易烂掉：加了接口忘了写文档。本章用 Spring MVC 的真实映射做对拍：

```java
@SpringBootTest
class ApiDocCoverageTest {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private ApiDocRegistry apiDocRegistry;

    @Test
    void everyControllerMappingShouldBeDocumented() {
        Set<String> actual = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            // 遍历 PathPatterns + HTTP 方法，排除 /error
        }
        Set<String> missing = new TreeSet<>(actual);
        missing.removeAll(apiDocRegistry.documentedKeys());
        assertTrue(missing.isEmpty(), "存在未文档化接口: " + missing);
    }
}
```

效果：以后**新增一个 Controller 方法但忘记登记文档，测试会直接失败**。这就是"可执行的文档纪律"。

### 8.1.4 生产替换为 springdoc/Knife4j 的步骤

1. 加依赖 `springdoc-openapi-starter-webmvc-ui`（或 `knife4j-openapi3-jakarta-spring-boot-starter`）；
2. 删除 `/swagger-ui.html` 的自实现页面即可，`/v3/api-docs` 由 springdoc 自动提供；
3. 如需保留 `ApiDocCoverageTest`，改为比对 springdoc 动态输出，或降级为回归用例；
4. Knife4j 增强：`knife4j.enable=true`，访问 `/doc.html` 可导出离线 HTML/Markdown。

### 8.1.5 怎么排错

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 页面空白 | `/v3/api-docs` 返回异常 | 先直接访问 JSON 排查 |
| 缺接口 | 未登记或路径写错 | 跑覆盖率测试，按提示补 |
| 中文字段乱码 | 响应编码问题 | 设置 `produces = APPLICATION_JSON_VALUE`（UTF-8） |
| 生产 UI 404 | 路径不对 | springdoc 是 `/swagger-ui/index.html` |

---

## 8.2 JUnit 5 + Mockito：给核心 Service 上保险

### 8.2.1 测试金字塔

```text
        少量集成测试（Testcontainers / @SpringBootTest）
      中量接口与并发测试（GrabConcurrencyTest、ApiDocCoverageTest）
    大量单元测试（Mockito 隔离依赖：TrainServiceTest、OrderServiceTest）
```

原则：**单元测试要快、要确定**；依赖数据库/Redis/外网的场景交给集成测试。

### 8.2.2 Mockito 三件套

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private StationMapper stationMapper;
    @Mock private TrainMapper trainMapper;
    @Mock private SeatInventoryMapper seatInventoryMapper;
    @Mock private TicketOrderMapper ticketOrderMapper;

    @InjectMocks private OrderService orderService;

    @Test
    void createOrderShouldDeductSegmentsAndInsertOrder() {
        stubJourney();
        when(seatInventoryMapper.selectSegmentsForUpdate(1L, TRAVEL_DATE, "二等座", 1, 3))
                .thenReturn(List.of(segment(5, "232.50"), segment(3, "344.50")));
        when(seatInventoryMapper.deductRange(1L, TRAVEL_DATE, "二等座", 1, 3)).thenReturn(2);

        OrderCreateVO vo = orderService.createOrder(request());

        assertEquals(0, vo.getPrice().compareTo(new BigDecimal("577.00")));
        assertEquals("待支付", vo.getStatus());
        verify(ticketOrderMapper).insert(any(TicketOrder.class));
    }
}
```

三个关键动作：

1. **`@Mock`**：造出假的 Mapper，不碰数据库；
2. **`when(...).thenReturn(...)`**：约定依赖行为（库存 2 段、扣减成功 2 行）；
3. **`verify(...)` / `assertThrows(...)`**：验证"该调用的调用了、不该调用的没调用、异常路径正确"。

### 8.2.3 本章覆盖的异常路径

| 用例 | 断言 |
| --- | --- |
| 正常下单 | 价格 = 区段票价之和、状态待支付、INSERT 被调用 |
| 余票不足 | 抛 2001，`deductRange` 从未调用，订单未插入 |
| 扣减冲突（影响行数不足） | 抛 2001，订单未插入 |
| 区间反向 | 抛 1002，库存与订单 Mapper 无交互 |
| `failAfterDeduct=true` | 抛 2002，但 INSERT 已发生（由事务回滚兜底） |
| 车站不存在 | 抛 1001（`TrainServiceTest`） |
| 出发到达相同 | 抛 400，且不查询 Mapper |

`@ExtendWith(MockitoExtension.class)` 默认严格模式：**未使用的 stub 会直接报错**，避免"假测试"。

### 8.2.4 Testcontainers 集成测试（方案就绪，未实机）

Testcontainers 用真实容器跑 MySQL/Redis 集成测试：

```java
@Testcontainers
@SpringBootTest(properties = {
        "railway.redis.mode=redis",
        "railway.grab.order-store=db"
})
class OrderServiceIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("railway")
            .withInitScript("sql/init.sql");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Test
    void concurrentGrabShouldNotOversellWithRealRedisAndMysql() {
        // 与 GrabConcurrencyTest 相同的并发断言，但走真实中间件
    }
}
```

接入步骤：`pom.xml` 加 `org.testcontainers:junit-jupiter`、`mysql`，且本机有 Docker；`sql/init.sql` 复制一份到 `src/test/resources/sql/` 供容器初始化。本机无 Docker 与依赖包，本章仅提供模板并标注**未实机验证**。

### 8.2.5 怎么排错

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| `UnnecessaryStubbingException` | stub 了没用的方法 | 删多余 stub 或改用 `lenient()`（不推荐） |
| `NullPointerException` in service | 忘了 `@InjectMocks` 或构造参数对不上 | 检查注解与构造函数 |
| 测试互相污染 | 静态状态/共享容器 | 每个测试独立造数据 |
| 并发测试偶发失败 | 用 `Thread.sleep` 代替同步 | 用 `CountDownLatch`/`CyclicBarrier` |

---

## 8.3 Git 提交规范

### 8.3.1 约定式提交

```text
feat(第5章): 实现时间扩展图与最快最经济换乘
fix(第4章): 修复换乘等待回溯为0的问题
docs(第8章): 生成README与打印总册
test(第7章): 新增部署配置守护测试
refactor(第6章): 抽取样例时刻表供AI工具复用
```

格式：`type(scope): 描述`。常用 type：`feat` / `fix` / `docs` / `test` / `refactor` / `chore`。

### 8.3.2 为什么

- 面试官看提交历史能快速判断你的工程习惯；
- 出问题能通过 `git log --oneline` 快速定位到功能点；
- 小步提交让每一步都可回滚、可测试。

### 8.3.3 常用命令

```bash
git status
git add src/main/java/com/railway/algorithm/TimeExpandedGraph.java docs/chapters/chapter-05.md
git commit -m "feat(第5章): 实现时间扩展图与双策略换乘"
git log --oneline -10
git diff --stat
```

> 本项目遵循"用户明确要求才提交"的约定：本章只沉淀规范，不代提交。

---

## 8.4 README 撰写：面试第一印象

### 8.4.1 面向谁写

面向铁路局技术岗面试官（尤其广铁）：他们时间有限，关心**业务理解、工程能力、量化结果、与岗位的契合度**。

### 8.4.2 结构模板（本项目实际采用）

```text
1. 标题 + 一句话定位 + 技术主线
2. 三句话看懂业务痛点（席位复用/防超卖/换乘候补）
3. 项目缘起（引用 docs/project-origin.md，不重复正文）
4. 核心亮点 STAR × 4（高并发/换乘算法/AI主线/广铁特色）
5. 技术栈表格
6. 量化指标表（每条注明来源，模拟验证单独标注）
7. 功能与接口表 + API 文档/算法/数据库链接
8. 快速开始（离线 / Docker / 真实LLM 三种方式）
9. 项目结构
10. 学习材料导航
11. 验证边界（如实说明未实机项）
12. 面试故事线（一页版）
```

### 8.4.3 写作三条底线

1. **量化必须真实**：写"100 并发抢 10 票 → 10 订单"就必须有测试/脚本能复现；内存模拟的数据必须标注"内存模拟验证"。
2. **痛点先行**：先讲铁路业务问题，再讲技术方案，避免"技术堆砌"。
3. **链接可点**：部署文件、API 文档、章节材料、导图都要给相对路径链接。

### 8.4.4 STAR 法则

```text
S（Situation）：业务背景与痛点，如"100 并发抢 10 张票必须不多不少"
T（Task）：你的目标，如"升级为分布式锁 + 原子扣减 + 削峰"
A（Action）：具体技术动作，如 Lua 脚本、看门狗续期、剪枝策略
R（Result）：量化结果，如"10 个订单、0 超卖、QPS 990"
```

写简历与面试回答时严格按 STAR，每段亮点都落到数字。

---

## 8.5 打印归档：从章节 HTML 到总册

### 8.5.1 每章打印版

```bash
node scripts/md2print.mjs docs/chapters/chapter-08.md docs/print/chapter-08.html \
  "第8章 项目收尾与包装" --mindmap docs/mindmaps/chapter-08-start.mmd
```

- A4 页边距、页眉页脚、h1 分页、代码块与表格防跨页；
- 思维导图以 **Markdown 缩进大纲**内嵌（离线、不依赖 JS/网络），SVG 仅在已预装 mmdc 时可选引用。

### 8.5.2 总册合并（本章新增脚本）

`scripts/merge-print.mjs` 用 Node 标准库完成：

1. 读取 `docs/print/chapter-00.html` ~ `chapter-08.html`，抽取每章 `<main class="chapter">` 内容；
2. 每章正文后追加该章 `chapter-XX-end.mmd` 的复盘导图缩进大纲；
3. 组装封面（项目名/技术主线/日期）、目录（锚点链接）、
   总起导图（`docs/mindmaps/00-overall.md` 与 `00-progress.md` 的缩进大纲）、
   第0-8章正文、项目结尾导图（`docs/mindmaps/99-final.md`）、附录（文件与验证说明）；
4. 复用章节 CSS（`@media print`、h1 分页、防跨页），确保直接打印正常。

```bash
node scripts/merge-print.mjs
```

产物：`docs/print/full-book.html`。

### 8.5.3 PDF（视环境而定）

- **浏览器打印**：打开 `full-book.html`，Ctrl+P → 另存为 PDF（零安装，推荐）；
- **Pandoc + LaTeX**（仅当已安装时）：

```bash
pandoc docs/chapters/*.md -o docs/print/full-book.pdf --toc \
  --pdf-engine=xelatex --variable CJKmainfont="Noto Sans CJK SC" \
  --variable geometry:margin=2cm
```

未安装则只给命令，不下载不安装（本项目遵守该红线）。

### 8.5.4 打印检查清单

打印检查清单（已在合并脚本与静态检查中逐项确认）：

- ✅ 封面、目录、总起导图、总进度图、第0-8章、项目结尾导图、附录齐全
- ✅ 每章 h1 前分页
- ✅ 导图为缩进大纲（无 SVG 依赖）
- ✅ 代码块、表格 `page-break-inside: avoid`
- ✅ 页眉页脚与页码
- ✅ UTF-8 中文正常

---

## 8.6 项目收尾成果

### 8.6.1 能力全景

```text
业务：席位复用 → 事务防超卖 → Redis锁+Lua+削峰 → 时间扩展图换乘 → 站内规则 → AI助手/RAG → 部署监控
数据：MySQL 索引与事务 → MyBatis → Redis → 向量检索 → 候补聚合SQL
工程：90个测试 → OpenAPI文档 → Docker Compose → Prometheus/Grafana → 打印总册与README
特色：广铁候补2小时决策 + 广州南站接驳；华交与单杏花叙事
```

### 8.6.2 验收结果

| 验收项 | 结果 |
| --- | --- |
| README 完整，含 STAR 与量化指标 | ✅ `README.md` |
| API 文档可访问、接口全覆盖 | ✅ `/swagger-ui.html`（未实机启动，覆盖率测试通过） |
| 单元测试通过 | ✅ 90 个测试全绿 |
| `full-book.html` 可打开 | ✅ 合并脚本产出，本地静态可开 |
| 打印版导图缩进大纲可读 | ✅ 各章内嵌大纲 |
| 总进度全部 ✅ | ✅ `docs/mindmaps/00-progress.md` |

---

## 8.7 测试与验证

```bash
.\mvnw.cmd test
```

```text
Tests run: 90, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

本章新增 10 个测试：`ApiDocCoverageTest` 2 个（接口覆盖与 OpenAPI 结构）、`TrainServiceTest` 3 个、`OrderServiceTest` 5 个；累计 90 个。

未实机项：HTTP 启动验证（8080 未运行）、Docker 编排、Testcontainers、Pandoc/LaTeX PDF，均已在相应章节标注。

---

## 8.8 本章代码地图

| 文件 | 职责 |
| --- | --- |
| `src/main/java/com/railway/config/ApiDocConfig.java` | 18 个接口的文档元数据 |
| `src/main/java/com/railway/apidoc/ApiDocRegistry.java` | OpenAPI 3.0 JSON 生成 |
| `src/main/java/com/railway/controller/ApiDocController.java` | `/v3/api-docs`、`/swagger-ui.html` |
| `src/test/java/com/railway/apidoc/ApiDocCoverageTest.java` | 接口与文档对拍 |
| `src/test/java/com/railway/service/TrainServiceTest.java` | 车次查询 Mockito 单测 |
| `src/test/java/com/railway/service/OrderServiceTest.java` | 下单链路 Mockito 单测 |
| `README.md` | 面向面试官的项目说明 |
| `scripts/merge-print.mjs` | 离线合并打印总册 |
| `docs/mindmaps/99-final.md` | 项目总复盘导图 |
| `docs/print/full-book.html` | 打印总册 |

---

## 8.9 高频面试问答

1. **"为什么 Mockito 测试要严格模式？"**
   未使用的 stub 说明测试与实现脱节，严格模式直接失败，逼迫测试保持真实。

2. **"单元测试与集成测试怎么分工？"**
   单测用 Mock 隔离依赖，验证业务分支与边界；集成测试用 Testcontainers 起真实 MySQL/Redis，验证 SQL、事务、并发等"只有真环境才暴露"的问题。

3. **"怎么保证接口文档不过期？"**
   用 `RequestMappingHandlerMapping` 枚举真实映射与文档注册表对拍，缺一项就测试失败；这就是"文档即测试"。

4. **"README 怎么写才像工程而非课程作业？"**
   痛点先行、STAR 量化、每项指标可复现、未验证项如实标注、部署与文档链接齐全。

5. **"Git 提交为什么要约定式？"**
   可读、可追溯、可自动化（生成 changelog、语义化版本），面试官能从历史看出工程习惯。

6. **"项目最大的难点是什么？"**
   示例回答：不是单点技术，而是"一致性链条"——缓存与 DB、锁与库存、AI 与真实数据、异步与回滚，每一步都要有兜底和验证。

---

## 8.10 自检三问

1. **是什么**：OpenAPI 描述接口、Mockito 隔离依赖做单测、Testcontainers 起真环境做集成测试、STAR 把亮点变成量化故事、离线脚本把八章合并成总册。
2. **为什么**：面试官先看 README 与文档，再看测试与量化；文档会过期、测试会腐化，所以要用"覆盖率测试 + 严格 Mockito"把它们钉住。
3. **怎么排错**：文档缺失跑覆盖率测试；单测报 UnnecessaryStubbing 删多余 stub；并发偶发失败用 CountDownLatch 替代 sleep；打印异常查分页 CSS 与 UTF-8。

---

## 8.11 项目完成之后

八章主线到此闭环。继续深入的方向：

1. **工程化**：把 CI（GitHub Actions）跑起来，自动执行 90 个测试 + 构建镜像 + 推送；接入 SonarQube 看代码质量趋势。
2. **数据规模**：接入 ShardingSphere 做订单分库分表，Seata 处理订单-库存-支付分布式事务。
3. **AI 进阶**：把 mock Embedding 换成真实向量模型与向量数据库，加入多轮对话、工具编排与评测集。
4. **可观测性**：SkyWalking Agent 实机接入，Prometheus 加告警规则与压测基线。
5. **铁路业务**：扩展同车接续、跨站换乘、票额动态释放等更深规则。

以上选学项（含 ShardingSphere、Seata、Spring Security、ElasticSearch、CI/CD、SonarQube、perf/eBPF、JVM、安全合规）的接入步骤与面试说法，见 [`docs/notes/supplement-topics.md`](../notes/supplement-topics.md)。

项目虽收尾，学习网络仍在生长——这正是总复盘导图 `docs/mindmaps/99-final.md` 想表达的事。
