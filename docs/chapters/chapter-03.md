# 第3章 MyBatis与Spring Boot整合

> 项目：铁路智慧出行综合服务平台
> 状态：✅ 已完成
> 建议耗时：第3-4周后段，约3-4天
> 深度：MyBatis L3（会用 Mapper、动态 SQL、ResultMap）；Spring 事务 L4（原理、传播行为、失效场景必须讲清楚）
> 一句话：把第2章的 SQL 装进 Mapper XML，通过 REST 返回车次列表，并用 `@Transactional` 守住"下单扣库存要么全成、要么全滚"。

---

## 本章目标

| 目标 | 具体产出 | 验收方式 |
| --- | --- | --- |
| MyBatis 整合 | starter 依赖 + `application.yml` 数据源/MyBatis 配置 | 应用启动，Mapper 注入成功 |
| 车次查询接口 | `GET /api/trains?from=&to=&date=` | curl 从 MySQL 返回车次列表 |
| 动态 SQL | `TrainMapper.xml` 按条件拼装 | 带/不带 trainType 返回结果不同 |
| ResultMap | Station/SeatInventory/TicketOrder 显式映射 | 列名与字段不同也能正确映射 |
| Spring 事务 L4 | `OrderService.createOrder` 下单扣库存 | 人为异常时扣减与订单同时回滚 |
| 统一返回结构 | `ApiResponse` + 全局异常处理 | 成功 code=0，失败返回业务错误码 |

配套材料：

- 开头导图：`docs/mindmaps/chapter-03-start.mmd`
- 数据源与 MyBatis 配置：`src/main/resources/application.yml`
- Mapper XML：`src/main/resources/mapper/`
- 上一章表结构与 SQL：`sql/init.sql`、`docs/architecture/01-database-design.md`

---

## 3.0 实验环境

前置条件：MySQL 已导入 `sql/init.sql`，`railway` 库存在（station 13 行、train 9 行、train_station 33 行、seat_inventory 947 行）。

启动应用：

```bash
mvnw.cmd spring-boot:run        # Windows
./mvnw spring-boot:run          # Linux / macOS
java -jar target/railway-12306-0.0.1-SNAPSHOT.jar
```

验证接口（本章验收命令）：

```bash
curl "http://localhost:8080/api/trains?from=北京南&to=上海虹桥&date=2026-04-15"
```

---

## 3.1 Spring Boot 自动配置：为什么只加依赖就能用

### 3.1.1 是什么

`@SpringBootApplication` 是一个组合注解：

| 注解 | 作用 |
| --- | --- |
| `@SpringBootConfiguration` | 声明配置类（本质是 `@Configuration`） |
| `@ComponentScan` | 扫描当前包及子包的 Bean |
| `@EnableAutoConfiguration` | 读取自动配置类，按条件生效 |

`@EnableAutoConfiguration` 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 找到候选配置类，再用 `@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty` 等条件决定装不装。

MyBatis 的自动配置来自 `mybatis-spring-boot-starter`：

```text
mybatis-spring-boot-starter
  └── mybatis-spring-boot-autoconfigure
        └── MybatisAutoConfiguration
              ├── @ConditionalOnSingleCandidate(DataSource.class)  有数据源才生效
              ├── 创建 SqlSessionFactory（吃 application.yml 的 mybatis.*）
              ├── 创建 SqlSessionTemplate
              └── AutoConfiguredMapperScannerRegistrar 扫描 @Mapper 接口
```

**为什么**：starter = 依赖聚合 + 自动配置。省掉手写 `SqlSessionFactoryBean`、`MapperScannerConfigurer` 的 XML 配置，这是 Spring Boot "约定优于配置"的体现。

**怎么排错**：

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 启动报 `Failed to configure a DataSource` | 没配 url 或依赖缺失 | 检查 `spring.datasource` 与 mysql 驱动 |
| Mapper 注入报 `NoSuchBeanDefinitionException` | 接口没被扫描 | 加 `@Mapper` 或 `@MapperScan("com.railway.mapper")` |
| 启动报 `Invalid bound statement (not found)` | XML 没被加载或 namespace 写错 | 检查 `mapper-locations` 与 namespace |

### 3.1.2 本项目配置逐项解读

`src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/railway?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
    hikari:
      maximum-pool-size: 10         # 连接池上限，按并发调整
      minimum-idle: 2
      connection-timeout: 3000      # 获取连接超时 3s，快速失败

mybatis:
  mapper-locations: classpath:mapper/*.xml   # XML 位置
  type-aliases-package: com.railway.entity   # XML 里可直接写 Train 而非全限定名
  configuration:
    map-underscore-to-camel-case: true       # train_no → trainNo 自动映射
    default-fetch-size: 100
    default-statement-timeout: 30            # 单条 SQL 30s 超时
```

要点：

- `serverTimezone=Asia/Shanghai` 保证 `LocalDate/LocalTime` 读写无时区偏移；
- `${DB_PASSWORD:}` 表示取环境变量，取不到用空串，避免把密码提交进 Git；
- `map-underscore-to-camel-case` 能省掉简单映射，但列名与属性名差异大时仍推荐显式 `ResultMap`（见 3.3）。

---

## 3.2 Mapper 接口与 XML：绑定规则与参数传递

### 3.2.1 是什么

MyBatis 的 Mapper 由两部分组成：

- Java 接口：定义方法（编译期类型安全）；
- XML（或注解）：写 SQL，通过 `namespace + id` 与接口方法绑定。

本项目统一用 XML，接口只留一行 `@Mapper`：

```java
@Mapper
public interface StationMapper {
    Station selectByName(String stationName);
}
```

```xml
<mapper namespace="com.railway.mapper.StationMapper">
    <select id="selectByName" resultMap="StationMap">
        SELECT id, station_code, station_name, city, bureau,
               affiliated_depot, station_class, is_hub
        FROM station
        WHERE station_name = #{stationName}
    </select>
</mapper>
```

绑定规则（缺一不可）：

1. `namespace` = 接口全限定名；
2. 标签 `id` = 接口方法名；
3. `parameterType` 可省略，MyBatis 自动推断；`resultType` / `resultMap` 二选一；
4. XML 位置要能被 `mapper-locations` 匹配到。

### 3.2.2 参数传递三种姿势

```java
Station selectByName(String stationName);                       // 单参数：名字随意
Train selectByTrainNo(@Param("trainNo") String trainNo);        // 多参数必须 @Param
int insert(TicketOrder order);                                  // JavaBean：直接取属性
```

多参数不加 `@Param` 时，MyBatis 用 `arg0/arg1` 或 `param1/param2`，可读性差且易错，本项目一律显式 `@Param`。

### 3.2.3 `#{}` 与 `${}`：预编译与拼接

| 写法 | 底层 | 安全性 | 场景 |
| --- | --- | --- | --- |
| `#{name}` | PreparedStatement 占位符 `?` | 防 SQL 注入 | 所有参数值 |
| `${name}` | 字符串直接拼接 | 有注入风险 | 表名/列名等 SQL 结构，且必须白名单校验 |

```xml
<!-- 正确：值用 #{} -->
WHERE station_name = #{stationName}

<!-- 危险：排序字段用 ${}，若前端传入 "id; DROP TABLE ..." 就出事 -->
ORDER BY ${sortColumn}
```

### 3.2.4 怎么排错

| 报错 | 根因 | 处理 |
| --- | --- | --- |
| `Invalid bound statement (not found)` | namespace/id 与接口对不上，或 XML 未加载 | 核对全限定名；查 `mapper-locations` |
| `BindingException: Parameter 'xxx' not found` | 多参数没加 `@Param` | 补注解 |
| `TooManyResultsException` | 期望一条却返回多条 | 检查唯一条件，或返回 `List` |
| 查出来字段全 null | 列名与属性不匹配 | 开驼峰映射或写 ResultMap |

---

## 3.3 ResultMap：显式映射与复杂结果

### 3.3.1 是什么

`ResultMap` 描述"数据库列 → Java 属性"的映射规则：

```xml
<resultMap id="StationMap" type="Station">
    <id column="id" property="id"/>
    <result column="station_code" property="stationCode"/>
    <result column="is_hub" property="hub"/>
    <!-- 其余 column 与 property 一一对应 -->
</resultMap>
```

- `<id>` 标识主键，MyBatis 用它做行唯一性判断（嵌套映射时尤其重要）；
- 没写到的列，若开了 `map-underscore-to-camel-case` 仍会自动映射；
- 复杂场景用 `<association>`（一对一）和 `<collection>`（一对多）把 JOIN 结果折叠成对象图。例如"查车次带全部经停站"就适合 `<collection>`；本项目车次查询按扁平 VO 返回，暂不需要。

### 3.3.2 本项目的三个 ResultMap

| 文件 | ResultMap | 为什么显式写 |
| --- | --- | --- |
| `StationMapper.xml` | `StationMap` | `is_hub → hub` 无法靠驼峰规则映射 |
| `SeatInventoryMapper.xml` | `SeatInventoryMap` | 库存表列多，显式映射避免字段名歧义 |
| `TicketOrderMapper.xml` | `TicketOrderMap` | 订单表列多且含时间字段，适合统一维护 |

枚举映射：`TicketOrderMapper.insert` 传入的 `status` 是 `TicketStatus` 枚举，MyBatis 默认 `EnumTypeHandler` 写库时存 `name()`（如 `PENDING_PAYMENT`），正好对上表里的 `ENUM('PENDING_PAYMENT', ...)`。

### 3.3.3 怎么排错

- 某个字段为 null：先看 `SELECT` 有没有查该列，再核对 ResultMap 的 column/property；
- 集合为空：`<collection>` 的 `ofType` 写没写、`<id>` 有没有配；
- 性能问题：`ResultMap` 的嵌套 select（N+1 查询）优先改成 JOIN + collection。

---

## 3.4 动态 SQL：一套 XML 应对多条件

### 3.4.1 是什么

MyBatis 用标签在 XML 里拼 SQL，常用：

| 标签 | 作用 | 本项目/示例 |
| --- | --- | --- |
| `<if>` | 条件成立才拼 | 车次类型筛选（TrainMapper） |
| `<where>` | 自动去掉开头 AND/OR | `WHERE 1=1` 的优雅替代 |
| `<set>` | UPDATE 自动去尾逗号 | 动态更新库存/订单 |
| `<foreach>` | 遍历集合，拼 IN / 批量 VALUES | 批量插入经停站 |
| `<choose>/<when>/<otherwise>` | 多选一 | 按不同维度排序 |

### 3.4.2 本项目实例：按车次类型筛选

`TrainMapper.xml`：

```xml
<if test="trainType != null and trainType != ''">
    AND t.train_type = #{trainType}
</if>
```

- `from/to/date` 是必填条件，不参与动态；
- `trainType` 可选：不传时 SQL 里就没有这个 AND，返回全部等级；传 `G` 只返回高铁；
- `test` 里判空写法不能省，否则 null 会拼出 `AND t.train_type = null`。

教学示例（本项目后续批量扣减的优化方向）：

```xml
UPDATE seat_inventory
<set>
    remaining_count = remaining_count - 1,
    version = version + 1,
</set>
WHERE id IN
<foreach collection="ids" item="id" open="(" separator="," close=")">
    #{id}
</foreach>
```

注意 `<set>` 会自动去掉最后多余逗号；本项目当前扣减用范围条件一次 UPDATE，不需要 `foreach`，但面试常问，必须看懂。

### 3.4.3 怎么排错

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 多出 `AND` 导致语法错误 | 手写 `WHERE` + `<if>` 没配 `<where>` | 把 WHERE 换成 `<where>` |
| `test` 判断失效 | OGNL 表达式写错 | 字符串判空写 `!= null and != ''` |
| 集合参数报 `Parameter 'ids' not found` | 没加 `@Param("ids")` | interface 补注解 |

---

## 3.5 实战：车次查询接口 `GET /api/trains`

### 3.5.1 请求与响应约定

```text
GET /api/trains?from=北京南&to=上海虹桥&date=2026-04-15[&seatType=二等座][&trainType=G]
```

- `from`、`to`、`date` 必填；
- `seatType` 默认二等座（本项目第2章库存以二等座为主要样例）；
- `trainType` 可选，对应 3.4 的动态 SQL。

### 3.5.2 调用链

```text
TrainController.queryTrains                     ← 收参、ISO 日期转换
  └─ TrainService.queryTrains                   ← 参数校验、站名存在性校验
       ├─ StationMapper.selectByName(from/to)   ← 校验出发/到达站
       └─ TrainMapper.queryTrains(...)          ← 动态 SQL 查询
            └─ MySQL: train + train_station 两次 JOIN + 库存子查询
```

`TrainService` 的三层校验：必填参数 → 出发到达不相同 → 两站都存在（否则抛 `BusinessException`，由全局异常处理器转成统一 JSON）。

### 3.5.3 SQL 逐段解析

```sql
SELECT t.id, t.train_no, ..., fs.station_name AS from_station, ts.station_name AS to_station,
       tfs.departure_time, tts.arrival_time,
       FLOOR((tts.day_offset * 1440 + TIME_TO_SEC(tts.arrival_time) / 60)
                 - (tfs.day_offset * 1440 + TIME_TO_SEC(tfs.departure_time) / 60)) AS duration_minutes,
       (tts.mileage_from_start - tfs.mileage_from_start) AS mileage,
       (SELECT MIN(i.remaining_count)
        FROM seat_inventory i
        WHERE i.train_id = t.id AND i.travel_date = #{travelDate} AND i.seat_type = #{seatType}
          AND i.from_station_order >= tfs.station_order
          AND i.to_station_order <= tts.station_order) AS remaining_count,
       (SELECT SUM(i.price)
        FROM seat_inventory i
        WHERE i.train_id = t.id AND i.travel_date = #{travelDate} AND i.seat_type = #{seatType}
          AND i.from_station_order >= tfs.station_order
          AND i.to_station_order <= tts.station_order) AS total_price
FROM train t
         JOIN train_station tfs ON tfs.train_id = t.id    -- 出发站经停记录
         JOIN station fs ON fs.id = tfs.station_id
         JOIN train_station tts ON tts.train_id = t.id    -- 到达站经停记录
         JOIN station ts ON ts.id = tts.station_id
WHERE fs.station_name = #{from}
  AND ts.station_name = #{to}
  AND tfs.station_order < tts.station_order               -- 保证方向正确
  AND t.status = 1
ORDER BY tfs.departure_time
```

四个关键点：

1. **同表两次 JOIN**：`train_station` 起别名 `tfs`（from）和 `tts`（to），分别接两个 `station`，这是"按 O-D 查经停车次"的标准写法；
2. **`tfs.station_order < tts.station_order`**：防止把反方向的车次也查出来；
3. **余票 `MIN`**：严格复用第2章的席位复用模型——整个乘车区间拆成若干原子区段，区间余票 = 各段最小值，瓶颈段决定能卖几张；
4. **票价 `SUM`**：各原子区段票价相加，与数据库设计中 `seat_inventory.price` 的区段定价一致；`day_offset` 处理跨天车次，避免出现负的历时。

对应索引：`seat_inventory.uk_segment` 覆盖余票子查询全部条件（第2章 EXPLAIN 实测 `type=range`、`key=uk_segment`、只扫 3 行）。

### 3.5.4 实测结果

```bash
curl "http://localhost:8080/api/trains?from=北京南&to=上海虹桥&date=2026-04-15"
```

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
      "totalPrice": 716.5
    }
  ],
  "timestamp": 1789399182922
}
```

- 返回 1 趟 G1：09:00 北京南发车，13:28 到上海虹桥，全程 4 小时 28 分；
- `remainingCount=3` 与第2章 SQL 实测一致（瓶颈区段济南西→南京南）；
- `trainTypeName/directionName` 是 Service 层由枚举翻译的中文名，接口不暴露魔法值。

错误场景（统一由 `GlobalExceptionHandler` 返回）：

| 请求 | code | message |
| --- | --- | --- |
| `to=火星站` | 1001 | 到达站不存在：火星站 |
| `from` 与 `to` 相同 | 400 | 出发站与到达站不能相同 |
| 缺少 `date` | 400 | 参数错误：Required request parameter 'date' ... |
| `date=2026-13-99` | 400 | 参数错误：Failed to convert ... LocalDate |
| body 非法 JSON | 400 | 参数错误：JSON parse error ... |

---

## 3.6 Spring 事务 L4：原理、传播行为、失效场景

### 3.6.1 是什么：`@Transactional` 背后的代理链

`@Transactional` 不是魔法，是三件事的组合：

1. **AOP 代理**：Spring 为标注了 `@Transactional` 的 Bean 创建代理（JDK 动态代理或 CGLIB）。调用方法时先经过 `TransactionInterceptor`；
2. **事务管理器**：`DataSourceTransactionManager` 负责从 `DataSource` 拿 `Connection`，`setAutoCommit(false)` 开启事务，方法正常返回则 `commit`，抛异常则 `rollback`；
3. **事务同步**：事务期间连接通过 `TransactionSynchronizationManager` 绑定到当前线程的 `ThreadLocal`，同一个事务内的所有 DAO 操作共用同一个连接。

调用链：

```text
OrderController.createOrder
  └─ [代理对象] OrderService.createOrder
       └─ TransactionInterceptor.invoke
            ├─ 开启事务（拿连接、绑 ThreadLocal）
            ├─ 目标方法：select ... FOR UPDATE → 校验 → UPDATE → INSERT
            └─ 提交 / 回滚（异常）
```

**为什么**：业务代码里只写一个注解，不用手动 `commit/rollback`，也保证多步 SQL 原子性。**代价**：一旦绕开代理（见 3.6.4），注解就"失灵"。

### 3.6.2 传播行为七种

| 传播行为 | 含义 | 典型场景 |
| --- | --- | --- |
| `REQUIRED`（默认） | 有事务则加入，没有则新建 | 绝大多数业务 |
| `REQUIRES_NEW` | 无论外面有没有，都新建独立事务 | 记录操作日志：主业务回滚，日志仍要落库 |
| `NESTED` | 在现有事务内开 Savepoint，可部分回滚 | 批量下单，单条失败不影响其它条 |
| `SUPPORTS` | 有就加入，没有就非事务执行 | 查询方法 |
| `NOT_SUPPORTED` | 挂起当前事务，非事务执行 | 发送通知等不关心回滚的操作 |
| `MANDATORY` | 必须已存在事务，否则抛异常 | 强制要求被事务方法调用 |
| `NEVER` | 有事务就抛异常 | 禁止在事务内调用 |

本项目 `OrderService.createOrder` 用默认的 `REQUIRED`：Controller 没有事务，因此方法本身开启一个新事务，扣库存与写订单在同一事务内。

### 3.6.3 隔离级别与回滚规则

- **隔离级别** `isolation` 默认 `DEFAULT`，跟随数据库（本项目 MySQL 为 RR），一般不要改——改级别是全局一致性问题；
- **回滚规则**：`@Transactional` 默认只对 `RuntimeException` 和 `Error` 回滚，**受检异常（checked）默认不回滚**。本项目写成：

```java
@Transactional(rollbackFor = Exception.class)
public OrderCreateVO createOrder(OrderCreateRequest request) { ... }
```

`rollbackFor = Exception.class` 表示任何异常都回滚，避免以后有人抛出受检异常时静默丢数据。

- `readOnly = true`：只读事务提示，查询方法可用（第2章余票查询走 MVCC 快照读，不需要锁）；
- `timeout`：事务超时秒数，防止长事务拖垮 undo 链。

### 3.6.4 高频面试题：`@Transactional` 失效的常见场景

| # | 场景 | 为什么会失效 | 规避 |
| --- | --- | --- | --- |
| 1 | **同类内部自调用** `this.createOrder()` | this 不是代理对象，不经过 TransactionInterceptor | 拆到另一个 Bean；注入自身代理；用 `AopContext.currentProxy()` |
| 2 | 方法不是 public | 代理只能拦截可覆写的公共方法 | 事务方法一律 public |
| 3 | `final` / `static` 方法 | CGLIB 无法继承/覆写 | 去掉 final/static |
| 4 | 异常被 `catch` 吞掉 | 拦截器感知不到异常，正常提交 | 捕获后 `throw` 或手动 `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` |
| 5 | 抛受检异常且没配 `rollbackFor` | 默认只回滚运行时异常 | `rollbackFor = Exception.class` |
| 6 | 对象不是 Spring Bean（`new OrderService()`） | 没有代理 | 交给容器注入 |
| 7 | 多线程调用 | 连接/事务绑定在调用方线程的 ThreadLocal，子线程不在同一事务 | 子线程单独开事务，主线程等待并汇总 |
| 8 | 数据库引擎不支持事务 | 如 MyISAM | 用 InnoDB |
| 9 | 传播行为配成 `NOT_SUPPORTED` / `NEVER` | 本来就不在事务里 | 检查传播配置 |

记忆口诀：**代理没走到、异常没抛出、配置没生效**。面试先答这三条，再展开到自调用、受检异常、多线程。

### 3.6.5 本项目实战：下单 + 扣库存 + 防超卖

`OrderService.createOrder`（`src/main/java/com/railway/service/OrderService.java:48`）的完整流程：

```text
1. 参数校验（userId/trainNo/travelDate/站名/乘客名）
2. 车站、车次存在性校验
3. 查区间站序 fromOrder / toOrder，校验 from < to
4. SELECT ... FOR UPDATE   ——当前读锁定区间内所有库存行，先锁后改
5. 逐段校验 remaining_count >= 1，不足抛 SEAT_SOLD_OUT
6. UPDATE ... WHERE remaining_count >= 1   ——数据库层最后兜底
7. INSERT ticket_order（待支付、15 分钟过期）
8. failAfterDeduct=true 时抛异常 ——验证整体回滚的测试开关
```

关键 SQL（`SeatInventoryMapper.xml`）：

```sql
SELECT ... FROM seat_inventory
WHERE train_id = ? AND travel_date = ? AND seat_type = ?
  AND from_station_order >= ? AND to_station_order <= ?
ORDER BY from_station_order
FOR UPDATE;          -- 区间内每一段库存行加锁，其它事务排队

UPDATE seat_inventory
SET remaining_count = remaining_count - 1, version = version + 1
WHERE ... AND remaining_count >= 1;   -- 余票不足则不更新，返回影响行数
```

三道防线：

| 层 | 手段 | 作用 |
| --- | --- | --- |
| 应用层 | `expectedSegments = toOrder - fromOrder`，影响行数不等就抛异常 | 防止部分区段扣减 |
| SQL 层 | `remaining_count >= 1` | 数据库层拒绝负库存 |
| 事务层 | `FOR UPDATE` + `@Transactional` | 先锁后校验，避免并发下"读到旧值再扣减" |

另外两个细节：

- **统一加锁顺序**：`ORDER BY from_station_order`，所有事务按同一顺序拿行锁，避免第2章讲过的死锁环路；
- **订单号生成**：`ORD + yyyyMMddHHmmss + 4 位随机数`，学习阶段够用；生产应交给号段/雪花算法。

### 3.6.6 事务实测记录（本次真实运行）

测试对象：G1 次 2026-04-15 二等座，北京南（站序 1）→ 上海虹桥（站序 4），区间含 3 个原子段。

| 步骤 | 区间库存（1-2 / 2-3 / 3-4） | 订单数 | 说明 |
| --- | --- | --- | --- |
| 初始状态 | 320 / 3 / 210 | 2 | 样例数据 |
| 正常下单成功 | 319 / 2 / 209 | 3 | 三段全部 -1，订单落库 |
| `failAfterDeduct=true` 模拟异常 | 319 / 2 / 209 | 3 | 三段已扣减的 UPDATE 被回滚，订单 INSERT 也未生效 |

正常下单响应：

```json
{
  "code": 0, "message": "成功",
  "data": {
    "orderId": 6,
    "orderNo": "ORD202609142319531432",
    "trainNo": "G1", "fromStation": "北京南", "toStation": "上海虹桥",
    "seatType": "二等座", "price": 716.50,
    "status": "待支付", "expireAt": "2026-09-14T23:34:53.4368786"
  }
}
```

模拟异常响应（扣库存、写订单都已执行，异常抛出后整体回滚）：

```json
{ "code": 2002, "message": "模拟异常：扣库存与订单必须一起回滚", "data": null }
```

验证命令：

```bash
# 1. 正常下单
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -d @order-success.json

# 2. 事务回滚：body 里加 "failAfterDeduct": true
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -d @order-fail.json

# 3. 对比前后库存与订单
#    SELECT from_station_order, to_station_order, remaining_count
#    FROM seat_inventory WHERE train_id=1 AND travel_date='2026-04-15' AND seat_type='二等座';
```

### 3.6.7 防超卖的分层演进

```text
第2章：CHECK(remaining_count >= 0) + UPDATE ... WHERE remaining_count >= 1
第3章：@Transactional + SELECT ... FOR UPDATE 先锁后扣 + 区间影响行数校验
第4章：Redis 分布式锁 + Lua 原子扣减 + MQ 异步削峰（1000 QPS）
```

本章用数据库事务解决"同库、单实例"的一致性；第4章解决"锁范围大、数据库压力大"的性能问题。面试按此顺序回答，体现演进思维。

---

## 3.7 本章代码地图

| 文件 | 职责 |
| --- | --- |
| `src/main/resources/application.yml` | 数据源、连接池、MyBatis 配置 |
| `src/main/java/com/railway/mapper/*.java` | 4 个 Mapper 接口 |
| `src/main/resources/mapper/TrainMapper.xml` | 车次查询（动态 SQL + 子查询余票/票价） |
| `src/main/resources/mapper/StationMapper.xml` | 车站查询 + ResultMap |
| `src/main/resources/mapper/SeatInventoryMapper.xml` | 区间库存 `FOR UPDATE` 锁定与扣减 |
| `src/main/resources/mapper/TicketOrderMapper.xml` | 订单写入、按订单号查询 |
| `src/main/java/com/railway/controller/TrainController.java` | `GET /api/trains` |
| `src/main/java/com/railway/controller/OrderController.java` | `POST /api/orders` |
| `src/main/java/com/railway/service/TrainService.java` | 车次查询业务校验与枚举翻译 |
| `src/main/java/com/railway/service/OrderService.java` | 下单事务：锁库存 → 扣减 → 写订单 |
| `src/main/java/com/railway/common/ApiResponse.java` | 统一返回结构 `{code,message,data,timestamp}` |
| `src/main/java/com/railway/exception/GlobalExceptionHandler.java` | 业务/参数/系统异常统一出口 |

---

## 3.8 自检三问（对齐验收标准）

1. **是什么**：MyBatis 靠 `namespace + id` 把 XML 与接口绑定；动态 SQL 用 `<if>/<where>/<foreach>` 拼装；Spring 事务靠 AOP 代理 + `DataSourceTransactionManager` 实现提交/回滚。
2. **为什么**：车次查询要同时过滤 O-D、方向、等级，还要取区间最小余票，所以必须动态 SQL + 子查询；下单是"扣库存 + 写订单"两步写操作，必须同事务，否则超卖或丢单。
3. **怎么排错**：接口 404/绑定异常先查 namespace 与 `mapper-locations`；事务不回滚先查是否自调用、异常是否被吞、`rollbackFor` 是否覆盖。

验收命令回顾：

```bash
# 车次列表从 MySQL 返回 ✅
curl "http://localhost:8080/api/trains?from=北京南&to=上海虹桥&date=2026-04-15"

# 事务一致性：人为异常后库存与订单均未变化 ✅
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" \
     -d '{"userId":9002,"trainNo":"G1","travelDate":"2026-04-15","fromStation":"北京南",\
          "toStation":"上海虹桥","seatType":"二等座","passengerName":"李四","failAfterDeduct":true}'
```

面试追问预演：

- "Mapper 接口没有实现类，为什么能注入？"→ MyBatis 用 JDK 动态代理生成实现，Spring 把它注册成 Bean；`@Mapper` 让扫描器识别接口。
- "`#{}` 和 `${}` 区别？"→ 预编译占位符 vs 字符串拼接，前者防注入。
- "自调用为什么事务失效？"→ 调用的是原始对象而不是代理对象，事务拦截器没有机会介入。
- "先扣库存还是先写订单？"→ 同一事务内顺序不影响正确性，但要统一加锁顺序；本项目先锁库存再写单，便于余票不足时快速失败。
- "受检异常会回滚吗？"→ 默认不会，必须 `rollbackFor = Exception.class`。

---

## 3.9 下一章预告

**第4章 高并发与 Redis**：本章的 `FOR UPDATE` 在单机小并发下没问题，但 1000 QPS 抢同一区间时，数据库行锁会成为瓶颈。下一章引入 Redis 缓存余票、SETNX/Redisson 分布式锁、Lua 原子扣减和 MQ 异步下单，目标：**100 并发抢 10 张票，恰好生成 10 个订单，零超卖**。
