# 第1章 Java核心基础与实体建模

> 项目：铁路智慧出行综合服务平台
> 状态：✅ 已完成
> 建议耗时：第1-2周后段，约3-4天
> 一句话：把铁路业务描述翻译成 Java 类，让控制台第一次"查得出车次"。

---

## 本章目标

| 目标 | 具体产出 | 验收方式 |
| --- | --- | --- |
| 掌握 OOP 三大特性 | 封装、继承、多态落到实体代码 | 能指出代码中三者分别在哪儿 |
| 熟练掌握集合框架 | List 存车次、Map 建车站索引、Set 收集站名 | 能解释为什么这样选型 |
| 完成实体建模 | Station、Train、Seat、Ticket 四类实体 | 字段合理，含等级/上下行/路局 |
| 完成控制台查询 | `ConsoleTrainQuery` 输入两站打印车次 | 运行后输出正确车次列表 |

本章只学 Java 基础的 L3 深度：会写、懂原理、能排错。**不深入 JVM 调优，不读集合源码**。

---

## 1.1 为什么第1章是"建模"

第0章我们知道了铁路业务长什么样：车次有等级、方向有上下行、站点属于不同路局、座位要按区段复用。接下来要做的第一件事，不是写接口，而是**把业务对象变成 Java 类**。

一个简单的判断：如果类设计得好，后面的数据库表、API 返回值、缓存结构都水到渠成；如果类设计得乱，后面每一章都在还债。所以本章宁可慢一点，把四个核心实体的字段想清楚：

```
业务语言                 Java 语言
车站、所属路局、站等级  →  Station
车次号、等级、上下行    →  Train
座位类型、定员、余票    →  Seat
订单号、乘车人、状态    →  Ticket
```

---

## 1.2 OOP 三大特性在铁路建模中的落地

### 1.2.1 封装：私有字段 + 公开方法

所有实体字段都用 `private` 修饰，外部只能通过 `getter/setter` 访问：

```java
public class Station extends RailwayEntity {

    private String stationCode;
    private String stationName;
    private String bureau;
    private String affiliatedDepot;

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }
}
```

封装的价值不只是"规范"，而是**留出控制点**：

- 以后要在 `setRemainingCount` 里校验"余票不能为负"，改一处即可；
- 以后字段改名（`stationName` → `name`），只影响类的内部，不影响调用方；
- 数据库映射框架（第3章 MyBatis）也依赖 getter/setter 取值、赋值。

### 1.2.2 继承：抽象基类 `RailwayEntity`

四类实体都有主键 `id`，也有共同的"系统归属"概念，于是抽出抽象基类：

```java
public abstract class RailwayEntity {

    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public abstract RailwaySystem getSystemCategory();
}
```

子类用 `extends` 继承：

```java
public class Station extends RailwayEntity {
    @Override
    public RailwaySystem getSystemCategory() {
        return RailwaySystem.CAR_SERVICE;
    }
}
```

为什么是**抽象类**而不是普通父类或接口？对比一下：

| 方案 | 能存字段吗 | 能强制子类实现方法吗 | 本场景是否合适 |
| --- | --- | --- | --- |
| 普通父类 | 能 | 不能 | 不合适，子类可能忘记分类方法 |
| 接口 | 不能（只能常量） | 能 | 可作补充，但 id 字段没处放 |
| 抽象类 | 能 | 能 | 最合适，字段 + 强制实现兼得 |

### 1.2.3 多态：同一个方法，不同的系统归属

`getSystemCategory()` 在不同子类里返回不同结果，这就是多态——**调用方只认父类类型，运行期执行子类实现**：

```java
List<RailwayEntity> entities = List.of(station, train, seat, ticket);
for (RailwayEntity entity : entities) {
    System.out.println(entity.getSystemCategory().getChineseName());
}
```

四类实体的系统归属如下：

| 实体 | 系统归属 | 理由 |
| --- | --- | --- |
| Station | 车务 | 车站由车务段管理，负责客运组织 |
| Train | 车辆 | 车底（动车组/客车）属于车辆系统 |
| Seat | 车辆 | 座位是车厢资源，随车底走 |
| Ticket | 车务 | 售票、乘车服务属于客运业务 |

> 面试小知识：铁路五大系统是"车、机、工、电、辆"——车务、机务、工务、电务、车辆。做铁路项目时能自然说出这个分类，比背八股更能体现业务理解。

---

## 1.3 实体设计与五大系统

### 1.3.1 车机工电辆一览

| 简称 | 系统 | 管什么 | 典型站段 |
| --- | --- | --- | --- |
| 车 | 车务 | 车站、调车、客运组织 | 车务段、客运段 |
| 机 | 机务 | 机车运用与检修 | 机务段 |
| 工 | 工务 | 线路、桥隧养护 | 工务段 |
| 电 | 电务 | 信号、通信设备 | 电务段 |
| 辆 | 车辆 | 客车、货车检修运用 | 车辆段、动车段 |

`Station` 的 `affiliatedDepot` 字段就用来标注"所属机务段或车辆段"，例如广州南站标注"广州动车段"。这样数据模型天然带着铁路的组织维度，而不是一个通用电商系统。

### 1.3.2 Station 字段设计

| 字段 | 类型 | 说明 | 未来对应列 |
| --- | --- | --- | --- |
| id | Long | 主键 | station.id |
| stationCode | String | 电报码，如 VNP | station_code |
| stationName | String | 站名，如北京南 | station_name |
| city | String | 所在城市 | city |
| bureau | String | 所属路局，如广铁集团 | bureau |
| affiliatedDepot | String | 所属机务段/车辆段 | affiliated_depot |
| stationClass | String | 站等级：特等/一等/二等 | station_class |
| hub | boolean | 是否枢纽站 | is_hub |

### 1.3.3 Train 字段设计（重点：等级、上下行、路局）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| trainNo | String | 车次号，如 G1、K599 |
| trainType | TrainType | 车次等级，由车次号前缀自动推导 |
| direction | Direction | 上下行（UP/DOWN） |
| startStation / endStation | String | 始发/终到站 |
| bureau | String | 担当路局，如广铁集团 |
| departureTime / arrivalTime | LocalTime | 发到时刻 |
| durationMinutes | int | 历时（分钟） |
| mileage | int | 全程里程 |
| stopStations | List\<String\> | 经停站（含始发终到，按站序排列） |

车次等级不是手填的，而是从车次号推导：

```java
public static TrainType fromTrainNo(String trainNo) {
    if (trainNo == null || trainNo.isEmpty()) {
        throw new IllegalArgumentException("车次号不能为空");
    }
    char prefix = Character.toUpperCase(trainNo.charAt(0));
    for (TrainType type : values()) {
        if (type.name().charAt(0) == prefix) {
            return type;
        }
    }
    return P;
}
```

`stopStations` 是按站序排列的集合，它直接支撑控制台查询：**出发站在集合中的下标必须小于到达站**，才说明这趟车经过这两站且方向正确。这也是第2章 `train_station` 经停表、第5章路径规划的数据基础。

### 1.3.4 Seat 字段设计

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| trainNo | String | 所属车次 |
| seatType | SeatType | 商务座/一等座/二等座/卧铺/硬座等 |
| totalCount | int | 定员 |
| remainingCount | int | 余票（本章为全程口径） |
| price | BigDecimal | 票价，金额必须用 BigDecimal |

> 注意：本章 `remainingCount` 是"全程余票"的简化模型。第2章会把它拆成按区段的库存（席位复用），第4章再解决并发扣减。

### 1.3.5 Ticket 字段设计

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| ticketNo | String | 车票号 |
| trainNo | String | 车次 |
| fromStation / toStation | String | 乘车区间 |
| seatType | SeatType | 席别 |
| travelDate | LocalDate | 乘车日期 |
| departureTime | LocalTime | 发车时刻 |
| price | BigDecimal | 实付金额 |
| carriageNo / seatNo | String | 车厢号、座位号 |
| passengerName | String | 乘车人 |
| status | TicketStatus | 待支付/已支付/已退票/已取消 |

这些字段几乎可以直接映射成第2章的 `ticket_order` 表（规划名 `order`，因 `order` 是 MySQL 保留字改用 `ticket_order`），第4章下单流程也只是给它补上支付时间、锁单超时等字段。

---

## 1.4 集合框架：List / Map / Set

### 1.4.1 三者对比

| 接口 | 特点 | 是否允许重复 | 是否有顺序 | 本项目用途 |
| --- | --- | --- | --- | --- |
| List | 有序、可按下标访问 | 允许 | 有 | 车次列表、经停站序列 |
| Set | 不重复 | 不允许 | 视实现而定 | 站名集合、去重 |
| Map | 键值对，按键查找 | 键唯一 | 视实现而定 | 站名 → Station 索引 |

### 1.4.2 实际代码中的用法

`TrainQueryService` 同时用到了三种集合：

```java
private final List<Train> trains;
private final Map<String, Station> stationIndex;

public TrainQueryService(List<Station> stations, List<Train> trains) {
    this.trains = new ArrayList<>(trains);
    this.trains.sort(Comparator.comparing(Train::getDepartureTime));
    this.stationIndex = new LinkedHashMap<>();
    for (Station station : stations) {
        this.stationIndex.put(station.getStationName(), station);
    }
}

public Optional<Station> findStation(String stationName) {
    return Optional.ofNullable(stationIndex.get(stationName));
}

public Set<String> stationNames() {
    return Collections.unmodifiableSet(stationIndex.keySet());
}
```

- **Map 用于车站索引**：查站是 O(1)，比遍历 List 快得多；用 `LinkedHashMap` 让站名按录入顺序输出；
- **List 用于车次列表**：需要顺序、需要排序（按发车时间）；
- **Set 包裹 `keySet()`**：对外暴露站名集合，加 `unmodifiable` 防止外部乱改。

### 1.4.3 常用实现选型

| 需求 | 推荐实现 | 原因 |
| --- | --- | --- |
| 频繁按下标读、尾部追加 | ArrayList | 数组结构，随机访问快 |
| 频繁头尾插入删除 | LinkedList | 链表结构，增删快（本项目暂不需要） |
| 按键快速查找 | HashMap | 哈希表，平均 O(1) |
| 需要键有序 | TreeMap | 红黑树，按 key 排序，O(log n) |
| 需要保持插入顺序 | LinkedHashMap | 哈希表 + 链表 |
| 去重且保持顺序 | LinkedHashSet | 哈希 + 链表 |

### 1.4.4 查询算法与复杂度

```java
public List<Train> query(String from, String to) {
    List<Train> matched = new ArrayList<>();
    for (Train train : trains) {
        int fromIndex = train.getStopStations().indexOf(from);
        int toIndex = train.getStopStations().indexOf(to);
        if (fromIndex >= 0 && toIndex >= 0 && fromIndex < toIndex) {
            matched.add(train);
        }
    }
    return matched;
}
```

- 每趟车做两次 `indexOf`，是 O(m)（m 为经停站数），整体 O(n×m)；
- 本章数据量小，够用；第3章换成 SQL + 索引，第5章换成图算法，不在这里过度优化；
- **排错提示**：如果查询结果为空，先打印 `train.getStopStations()`，确认站名是否与索引里完全一致（"北京南"和"北京"是两个站）。

---

## 1.5 异常处理

### 1.5.1 异常分类

| 类别 | 代表 | 是否必须处理 | 使用场景 |
| --- | --- | --- | --- |
| 受检异常（Checked） | IOException、SQLException | 必须 try 或 throws | 外部资源、可恢复问题 |
| 运行时异常（Unchecked） | NullPointerException、IllegalArgumentException | 不强制 | 参数错误、编程错误 |
| 自定义异常 | StationNotFoundException | 继承运行时异常更省事 | 业务规则失败 |

### 1.5.2 自定义业务异常

```java
public class StationNotFoundException extends RuntimeException {

    public StationNotFoundException(String stationName) {
        super("未找到车站：" + stationName);
    }
}
```

为什么继承 `RuntimeException` 而不是 `Exception`？因为"输入了不存在的车站"是调用方的参数问题，不需要强制上层每处都写 try；控制台入口统一捕获即可，将来 Spring Boot 里则用 `@RestControllerAdvice` 统一处理。

### 1.5.3 Optional + 自定义异常配合

```java
Station fromStation = service.findStation(from)
        .orElseThrow(() -> new StationNotFoundException(from));
```

`findStation` 返回 `Optional<Station>` 表达"可能查不到"，`orElseThrow` 在空值时抛出业务异常，避免返回 null 后到处判空。

### 1.5.4 try-with-resources 自动关资源

```java
try (Scanner scanner = new Scanner(System.in)) {
    while (scanner.hasNextLine()) {
        // ...
    }
}
```

`Scanner` 实现了 `AutoCloseable`，放在 try 的括号里，无论正常结束还是异常退出都会自动关闭，等价于手写 `finally { scanner.close(); }`，但更简洁。

### 1.5.5 控制台如何统一兜底

```java
try {
    // 查询与打印
} catch (StationNotFoundException | IllegalArgumentException e) {
    System.out.println("查询失败：" + e.getMessage());
}
```

两个异常合并捕获，只给用户一句友好提示，不让堆栈糊满屏幕。程序不退出，用户可以继续输入。

---

## 1.6 Maven 依赖管理

### 1.6.1 本章 pom 现状

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.16</version>
</parent>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

父工程负责**版本仲裁**：spring-web、jackson、tomcat 等几十个传递依赖的版本都不用我们写。

### 1.6.2 传递依赖

```
spring-boot-starter-web
├── spring-boot-starter          （核心自动配置）
├── spring-boot-starter-json     （JSON 序列化）
├── spring-boot-starter-tomcat   （内嵌容器）
└── spring-webmvc                （MVC）
```

`mvn dependency:tree` 可以看到完整依赖树（第0章学过，本章动手看一眼）。

### 1.6.3 依赖冲突与调解规则

当两条依赖链引入同一个 artifact 的不同版本时，Maven 按以下规则选一个：

1. **最短路径优先**：离本工程层级少的版本胜出；
2. **路径相同看声明顺序**：先声明的胜出。

排查命令：

```bash
mvn dependency:tree -Dincludes=com.fasterxml.jackson.core:jackson-databind
```

### 1.6.4 排除传递依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-logging</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

排除后通常会补一个想要替代的依赖（例如换成 log4j2 starter），否则可能缺日志实现。

### 1.6.5 dependencyManagement 与 BOM

父工程用 `<dependencyManagement>` 统一声明版本，子工程引用时**不写版本号**：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.railway</groupId>
            <artifactId>railway-common</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

以后项目拆多模块时（如 `railway-ai`、`railway-common`），这一条是基础。

### 1.6.6 本章踩坑：多个 main 方法

本章新增了 `ConsoleTrainQuery`，它也有 `main` 方法。Spring Boot 插件打包时会**找不到唯一的主类**，于是我们显式指定：

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <mainClass>com.railway.RailwayApplication</mainClass>
    </configuration>
</plugin>
```

控制台类仍然可以用 `java -cp target/classes` 直接运行，两者互不干扰。

---

## 1.7 运行与验收

### 1.7.1 控制台运行方式

交互模式（无参数运行，输入 `出发站 到达站`，输入 `exit` 退出）：

```bash
mvnw.cmd compile
java -cp target/classes com.railway.console.ConsoleTrainQuery
```

传参模式（适合快速验证）：

```bash
java -cp target/classes com.railway.console.ConsoleTrainQuery 北京南 南京南
java -cp target/classes com.railway.console.ConsoleTrainQuery 北京南 深圳北
java -cp target/classes com.railway.console.ConsoleTrainQuery 广州南 武汉
```

### 1.7.2 预期输出示例

```text
出发站：北京南（北京局集团，特等站，北京动车段）
到达站：南京南（上海局集团，特等站，南京动车段）
匹配车次（北京南 → 南京南）共 2 趟：
G11    高速动车组  下行   发 08:00 到 12:48  1480km  288分钟  担当：北京局集团  系统：车辆
       经停：北京南 > 济南西 > 南京南 > 杭州东
G1     高速动车组  下行   发 09:00 到 13:28  1318km  268分钟  担当：上海局集团  系统：车辆
       经停：北京南 > 济南西 > 南京南 > 上海虹桥
```

### 1.7.3 验收清单

- ✅ 输入出发站、到达站，控制台打印匹配车次（按发车时间排序）
- ✅ 实体类包含车次等级、上下行、所属路局、系统分类等字段
- ✅ Map 用于车站索引、List 用于车次列表、Set 对外暴露站名
- ✅ 不存在的车站给出友好错误提示
- ✅ Spring Boot 项目与 `/hello` 接口不受影响

---

## 1.8 自检三问

1. **封装**：为什么实体字段用 private + getter/setter，而不是 public？
   答：可以加校验、改内部实现不影响外部、便于框架映射。
2. **集合**：查询车次为什么用 List 存车次、Map 存车站？
   答：车次需要顺序和排序；车站需要按名快速查找，Map 是 O(1)。
3. **排错**：控制台查询没结果时先查什么？
   答：先确认站名拼写与索引一致，再打印经停站列表核对站序（fromIndex < toIndex）。

---

## 1.9 下一章预告

**第2章 MySQL 数据库设计与 SQL 实战**：把本章四个实体落成七张表，`seat_inventory` 用"车次 + 区段"实现席位复用，用 `EXPLAIN` 学会加索引。本章的 `stopStations` 序列将变成 `train_station` 经停表。
