# 第4章 高并发与Redis实战

> 项目：铁路智慧出行综合服务平台
> 状态：✅ 已完成
> 建议耗时：第5-6周，约5-7天
> 深度：Java并发编程 L4 + Redis L4 双专题；消息队列 L3
> 一句话：把第3章"数据库事务防超卖"升级为"Redis 分布式锁 + Lua 原子扣减 + MQ 异步削峰"，用 100 并发抢 10 张票验证零超卖。

---

## 本章目标

| 目标 | 具体产出 | 验收方式 |
| --- | --- | --- |
| Java 并发 L4 | 线程池、synchronized、ReentrantLock、CAS、AQS、ConcurrentHashMap、原子类 | 能解释 i++ 为什么不安全、CAS 与 synchronized 区别 |
| Redis L4 | 五大数据结构、过期策略、缓存三兄弟、SETNX、Redisson 可重入锁、Lua | 能写分布式锁并说清锁过期、误删、续期 |
| 分布式锁 | `DistributedLockManager` + Redis 实现 + 内存兜底实现 | 20 线程 10000 次自增无丢失 |
| 抢票接口 | `POST /api/order/grab`（同步/异步双模式） | 100 并发抢 10 张票恰好 10 个订单 |
| MQ 异步削峰 | `GrabQueue` 内存队列 + 4 消费者 + 虚拟排队位次 | 提交 QPS 与端到端 QPS 可量化 |
| 压测脚本 | JUnit5 并发测试 + `scripts/grab-stress.ps1` | 记录 QPS、成功数、订单数、剩余库存 |

配套材料：

- 开头导图：`docs/mindmaps/chapter-04-start.mmd`
- 结尾复盘：`docs/mindmaps/chapter-04-end.mmd`
- 压测脚本：`scripts/grab-stress.ps1`
- 上一章事务防超卖：`docs/chapters/chapter-03.md` 3.6.5 节

---

## 4.0 实验环境与双模式说明

本章真实环境检测结果：

| 组件 | 本机状态 | 处理方式 |
| --- | --- | --- |
| MySQL 8.4 | 未运行 | 抢票接口默认走内存订单模式，DB 模式代码保留 |
| Redis | 未安装 | Redis 锁与 Lua 代码完整保留，默认走内存兜底实现 |
| Kafka/RocketMQ | 未安装 | 用 `ArrayBlockingQueue` 内存队列模拟异步下单 |
| JMeter/wrk | 未安装 | 用 JUnit5 多线程 + PowerShell HttpClient 并发脚本替代 |
| Docker | 未安装 | 不依赖容器，文档给真实接入步骤，不执行下载安装 |

双模式由 `application.yml` 两个开关控制，代码路径完全一致，只有底层实现切换：

```yaml
railway:
  redis:
    mode: memory        # memory: 内存兜底(本机验证)  redis: 真实Redis
  grab:
    queue-capacity: 200      # 内存MQ队列容量(削峰水位)
    worker-count: 4          # 异步消费线程数
    lock-wait-millis: 3000   # 获取锁最长等待
    lock-lease-millis: 5000  # 锁租约(TTL)
    request-ttl-millis: 300000
    order-store: memory      # memory: 内存订单(本机验证)  db: MySQL订单
    simulate-order-cost-millis: 0
```

切换真实环境只需两步：启动 Redis 与 MySQL 后，把 `mode` 改为 `redis`、`order-store` 改为 `db`，再把库存用请求体预热一次。真实接入 Redis/MQ 的完整步骤见 4.3.5 与 4.4.5。

抢票整体架构：

```text
POST /api/order/grab
      │ 参数校验 + 生成 requestId
      ▼
GrabQueue 内存队列(ArrayBlockingQueue, 容量200)
      │ offer 成功立即返回 QUEUED + 排队位次          ← 削峰：HTTP 不等库存
      ▼
grab-worker-0..3 四个消费者线程
      │
      ▼
GrabProcessor.process
  1. lockManager.getLock("railway:lock:grab:G1:2026-04-15:二等座")
  2. lock.tryLock(3000ms, 5000ms)                      ← Redis SETNX+Lua / 内存兜底
  3. stockStore.deduct(stockKey, 1)                    ← Redis Lua 原子扣减
  4. orderWriter.create(request)                       ← DB事务 / 内存订单
  5. 抛异常则 stockStore.compensate(stockKey, 1)       ← 失败回补
  6. finally lock.unlock()                             ← 校验持有者后删除
      │
      ▼
结果写入 GrabResultRepository，GET /api/order/grab/{requestId} 查询
```

内存模式下 HTTP 压测结论（先给结论，过程见 4.7）：

| 模式 | 并发/库存 | 受理成功 | 服务端成功 | 失败 | 订单数 | 剩余 | 提交耗时 | 提交QPS |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sync 同步 | 100/10 | 100 | 10 | 90 | 10 | 0 | 101ms | 990.1 |
| async 异步 | 100/10 | 100 | 10 | 90 | 10 | 0 | 76ms | 1315.8 |

---

## 4.1 Java 并发编程专题 L4

### 4.1.1 线程池：七个参数决定吞吐与稳定性

**是什么**

`ThreadPoolExecutor` 的构造参数共七个：`corePoolSize`、`maximumPoolSize`、`keepAliveTime`、`unit`、`workQueue`、`threadFactory`、`handler`。执行流程：

```text
execute(task)
  ├─ 运行线程数 < coreSize           → 新建核心线程执行
  ├─ 否则尝试入队 workQueue           → 入队成功等待
  ├─ 队列满 && 线程数 < maxSize      → 新建非核心线程
  └─ 队列满 && 线程数 == maxSize     → 触发拒绝策略
```

四种拒绝策略：`AbortPolicy`（默认抛 `RejectedExecutionException`）、`CallerRunsPolicy`（提交线程自己执行，天然降速）、`DiscardPolicy`（静默丢弃）、`DiscardOldestPolicy`（丢最老任务）。

**为什么**

抢票接口的 100 个请求如果一人一线程，1000 QPS 时线程创建销毁与上下文切换会拖垮机器。线程池把线程复用、任务排队、过载拒绝三件事集中管理。本项目的消费者池 `grab-worker-0..3` 就是固定 4 线程的池化思想：HTTP 只负责入队，消费能力与线程数解耦。

**怎么排错**

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 任务不执行、队列越堆越满 | 队列用了无界 `LinkedBlockingQueue`，永远不会触发扩容与拒绝 | 换 `ArrayBlockingQueue` 并设容量 |
| CPU 打满、GC 频繁 | 线程数/队列过大，任务积压 | 调小队列，加监控 `getQueue().size()` |
| 线程数不涨过 coreSize | 队列没满，流程走不到新建非核心线程 | 理解执行顺序：先入队后扩容 |
| 抛 `RejectedExecutionException` | 过载 | 选择合适拒绝策略，前端返回 2003 排队已满 |
| 线程泄漏 | 线程池未 `shutdown` | 应用关闭时统一关闭 |

**示例代码（本项目生产级参数写法）**

```java
int coreSize = Runtime.getRuntime().availableProcessors();
ThreadPoolExecutor executor = new ThreadPoolExecutor(
        coreSize, coreSize * 2, 60L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(200),
        new ThreadFactoryBuilder().setNameFormat("grab-worker-%d").build(),
        new ThreadPoolExecutor.CallerRunsPolicy());
```

本项目的内存队列 `GrabQueue` 与线程池同源：`new ArrayBlockingQueue<>(capacity)` + 固定数量消费者循环 `poll(200ms)`，见 `src/main/java/com/railway/service/concurrency/GrabQueue.java`。

### 4.1.2 i++ 为什么不安全：一行代码拆成三步

**是什么**

`i++` 在字节码层是"读-改-写"三步：

```text
getfield   // 读 i 到操作数栈
iconst_1
iadd       // 加 1
putfield   // 写回 i
```

两个线程可能都读到 10，各自算出 11，先后写回，最终只加了 1 次，这就是"丢失更新"。

**为什么**

栈帧是线程私有的，i 是堆上共享变量，三步之间没有原子性保证，也没有内存屏障保证可见性。

**怎么排错**

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 压测计数总比预期小 | 计数用了 i++ | 换 `AtomicInteger` / `LongAdder` |
| 加了 volatile 仍不准确 | volatile 只保证可见性不保证原子性 | 原子类或加锁 |
| 双重检查单例出问题 | 指令重排 | 实例字段加 `volatile` |

**示例代码（本项目的对照实验）**

`NonAtomicDeductTest` 用 `CyclicBarrier` 让 100 个线程先读完 `stock.get()` 再一起写回：

```java
int current = stock.get();      // 100个线程都读到 10
barrier.await();                 // 对齐，保证"读"都发生在"写"之前
if (current >= 1) {
    success.incrementAndGet();   // 100人都以为自己抢到
    stock.set(current - 1);      // 最终库存只剩 9
}
```

实测：`success=100`、`stock=9`——10 张票"卖出"100 张、库存只减 1，丢失 99 次更新。随后同一场景改用 `InMemoryStockStore.deduct`（CAS 循环）后：`success=10`、`stock=0`，完全正确。

### 4.1.3 synchronized：对象头里的 Monitor

**是什么**

`synchronized` 编译为 `monitorenter/monitorexit`，锁信息记录在对象头的 Mark Word：

| 状态 | Mark Word 内容 | 适用 |
| --- | --- | --- |
| 无锁 | hashCode/分代年龄 | 初始 |
| 偏向锁 | 偏向线程 ID | 单线程反复进入（JDK15 后默认禁用） |
| 轻量级锁 | 栈中锁记录指针 | 少量竞争，CAS 自旋 |
| 重量级锁 | Monitor 指针 | 激烈竞争，内核挂起 |

锁升级不可逆：偏向锁 → 轻量级锁 → 重量级锁。`synchronized` 可重入：同一线程再次进入，计数器 +1。

**为什么**

它是 JVM 内置锁，语法简单、自动释放（异常也会释放），是 `ConcurrentHashMap`（JDK8）与内存锁的基础。

**怎么排错**

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 应用假死、CPU 不高 | 线程互相等锁形成死锁 | `jstack <pid>` 查 `Found one Java-level deadlock` |
| 多把锁顺序不一致 | 环路等待 | 统一加锁顺序（第3章库存已按站序加锁） |
| 锁粒度太大 | 串行化严重 | 锁分段/缩小临界区 |

**示例代码**

```java
private final Object lock = new Object();

public void deduct() {
    synchronized (lock) {
        if (stock > 0) {
            stock--;
        }
    }
}
```

### 4.1.4 ReentrantLock 与 AQS：显式锁与队列同步器

**是什么**

`ReentrantLock` 底层是 AQS（AbstractQueuedSynchronizer）：一个 `volatile int state` + 一条 CLH 双向等待队列。

```text
lock()  → CAS 抢 state 0→1；失败则包装成 Node 入队，park 挂起
unlock()→ state-1；为 0 时唤醒队首线程
```

公平锁按队列顺序唤醒；非公平锁允许插队（新线程直接 CAS，成功即抢到，吞吐更高）。`Condition` 提供 `await/signal` 条件队列。

**为什么**

相比 `synchronized`，它支持可中断获取（`lockInterruptibly`）、超时获取（`tryLock(timeout)`）、公平模式与多条件变量；分布式锁的本地兜底实现也需要"尝试+超时"语义。

**怎么排错**

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 忘记 unlock 导致永久阻塞 | 未用 try/finally | `tryLock` 后必须在 `finally` 释放 |
| 锁被别的线程释放 | 锁对象被跨线程传递 | 记录持有线程，释放前校验 |
| 公平锁吞吐骤降 | 排队唤醒开销 | 无特殊需求用非公平 |

**示例代码（本项目锁接口）**

```java
public interface DistributedLock {
    boolean tryLock(long waitMillis, long leaseMillis);
    void unlock();
    String getKey();
}
```

`GrabProcessor` 的标准使用姿势：

```java
DistributedLock lock = lockManager.getLock(key);
boolean locked = false;
try {
    locked = lock.tryLock(3000, 5000);
    if (!locked) {
        return GrabResult.failed(requestId, submittedAt, 2002, "获取分布式锁超时，请重试");
    }
    // 扣库存 + 下单
} finally {
    if (locked) {
        lock.unlock();
    }
}
```

### 4.1.5 CAS 与原子类：无锁并发的基石

**是什么**

CAS（Compare And Swap）是一条 CPU 原子指令：比较内存值是否等于期望值，相等则更新，否则失败重试。Java 通过 `Unsafe.compareAndSwapInt` 暴露，`AtomicInteger` 封装为 `compareAndSet`。

原子类家族：

| 类 | 特点 | 适用 |
| --- | --- | --- |
| `AtomicInteger/AtomicLong` | CAS 自旋 | 低竞争计数 |
| `LongAdder` | 分段累加再求和 | 高竞争计数（本项目统计计数器） |
| `AtomicReference` | 对象引用 CAS | 状态机 |
| `AtomicStampedReference` | 带版本号 | 解决 ABA |
| `AtomicIntegerArray` | 数组元素原子更新 | 分段库存 |

**为什么**

无锁、无上下文切换，低竞争时性能优于锁。CAS 的三大问题：ABA（用版本号解决）、自旋开销（高竞争时 CPU 空转）、只能保证一个变量原子性（多变量用锁或 `AtomicReference` 打包）。

**怎么排错**

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| CAS 一直失败、CPU 高 | 高竞争自旋 | 换 `LongAdder` 或加锁 |
| 数值"改回去了" | ABA | `AtomicStampedReference` |
| 原子类字段被替换 | 字段非 final | 保证原子类实例 final |

**示例代码（本项目库存扣减）**

```java
public StockDeductResult deduct(String key, int count) {
    AtomicInteger stock = stocks.get(key);
    if (stock == null) {
        return StockDeductResult.NOT_INITIALIZED;
    }
    while (true) {
        int current = stock.get();
        if (current < count) {
            return StockDeductResult.NO_STOCK;
        }
        if (stock.compareAndSet(current, current - count)) {
            return StockDeductResult.OK;
        }
    }
}
```

### 4.1.6 ConcurrentHashMap：从分段锁到 CAS + synchronized

**是什么**

JDK7：`Segment` 分段锁，默认 16 段，锁一段不影响其他段。
JDK8：丢弃分段锁，改为"数组 + 链表 + 红黑树"，用 `CAS` 插入空桶、`synchronized` 锁单个桶头节点；链表长度 ≥ 8 且数组长度 ≥ 64 时转红黑树。

**为什么**

抢票结果仓库 `GrabResultRepository` 用 `ConcurrentHashMap` 存 requestId → 结果，100 并发读写不需要自己加锁；`get` 无锁、`put` 只锁桶。

**怎么排错**

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 复合操作仍不安全 | `get` 后 `put` 不是原子 | 用 `compute/computeIfAbsent/merge` |
| 遍历时数据不一致 | 弱一致性迭代器 | 业务上接受或换快照 |
| 扩容期间卡顿 | 多线程协助扩容 | 预估容量初始化，减少扩容 |

**示例代码**

```java
Map<String, GrabResult> results = new ConcurrentHashMap<>();
results.computeIfAbsent(requestId, id -> new GrabResult());
```

### 4.1.7 AQS 深挖：state 与等待队列

**是什么**

AQS 用模板方法把"同步状态"抽象成 `state`：

- 独占模式：`tryAcquire/tryRelease`（ReentrantLock）
- 共享模式：`tryAcquireShared/tryReleaseShared`（Semaphore、CountDownLatch）
- 条件队列：`ConditionObject`，`await` 释放锁入条件队列，`signal` 转移到同步队列

**为什么**

线程池的 Worker、`ReentrantLock`、`CountDownLatch` 全部建立在 AQS 上，理解 state 与队列就理解了 JUC 的骨架。

**怎么排错**

自行实现 AQS 子类时忘记在 `tryAcquire` 里用 CAS 修改 state，会导致挂起线程无法被唤醒；排错时先看 state 值，再看队列节点状态。

**本章实际用到的并发工具**

| 工具 | 用途 | 位置 |
| --- | --- | --- |
| `ExecutorService` | 压测 100 并发线程池 | `GrabConcurrencyTest` |
| `ArrayBlockingQueue` | 有界内存队列削峰 | `GrabQueue` |
| `CountDownLatch` | 发令枪，让 100 线程同时起跑 | 全部并发测试 |
| `CyclicBarrier` | 对齐"读后写"，稳定复现丢失更新 | `NonAtomicDeductTest` |
| `AtomicInteger/Long` | 库存 CAS、统计计数 | `InMemoryStockStore`、`GrabResultRepository` |

---

## 4.2 Redis 实战专题 L4

### 4.2.1 五大数据结构与选型

| 结构 | 特点 | 典型场景 | 本章用法 |
| --- | --- | --- | --- |
| String | 二进制安全、可计数 | 缓存、计数器、分布式锁 | `railway:stock:*` 库存、锁 Hash 的值 |
| Hash | 字段级更新 | 对象缓存、购物车 | 锁结构：field=持有者，value=重入次数 |
| List | 双向链表 | 消息队列、最新列表 | 仅作知识储备 |
| Set | 去重、交并集 | 标签、共同关注 | 仅作知识储备 |
| ZSet | 有序、分值 | 排行榜、延时队列 | 仅作知识储备（候补排序可用） |

选型口诀：单值 String，对象 Hash，去重 Set，排序 ZSet，队列 List/Stream。

**怎么排错**

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| `WRONGTYPE` | 同一 key 混用多种结构 | `TYPE key` 确认，规范 key 命名 |
| 大 key 操作卡顿 | String 存了上万字段的 JSON | 拆 Hash、控制 value 大小 |
| 热 key 打满单分片 | 单一车次库存集中 | 本地缓存、key 加盐分片（第7章） |

### 4.2.2 过期策略与内存淘汰

**是什么**

Redis 过期删除是"惰性删除 + 定期删除"组合：访问 key 时检查过期（惰性），后台每 100ms 随机抽查部分 key（定期）。内存达到 `maxmemory` 后触发淘汰策略：

| 策略 | 含义 |
| --- | --- |
| `noeviction` | 不淘汰，写入报错 |
| `volatile-lru / volatile-lfu / volatile-random / volatile-ttl` | 只在设了过期时间的 key 中淘汰 |
| `allkeys-lru / allkeys-lfu / allkeys-random` | 全部 key 参与淘汰 |

**为什么**

分布式锁必须有过期时间：客户端崩溃后锁能自动释放，否则死锁。库存缓存同样需要 TTL 或显式清理，防止脏数据长期驻留。

**怎么排错**

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 内存上涨、key 不失效 | 没设 TTL 且策略是 noeviction | 设置过期时间、调淘汰策略 |
| 刚写入的缓存马上没了 | `volatile-*` 策略下 TTL 被优先淘汰 | 评估策略与容量 |
| 锁永不过期 | 只 SETNX 没 EXPIRE | 用 `SET key value NX PX ttl` 一步完成 |

**示例代码**

```bash
SET railway:lock:grab:G1:2026-04-15:二等座 holder-1 NX PX 5000
```

> 说明：这条命令只演示 `SET NX PX` 的原子加锁。本项目实现的是**可重入锁**，锁 key 实际为 Hash 结构（field=持有者 `clientId:threadId`，value=重入次数），加锁/释放走 4.4.2 的 Lua；直接对同一个 key 先 `SET` 再跑 Lua 会报 `WRONGTYPE`，生产请统一走 `DistributedLock` 接口。

### 4.2.3 缓存穿透、击穿、雪崩：高频面试三兄弟

| 问题 | 定义 | 后果 | 方案 |
| --- | --- | --- | --- |
| 穿透 | 查不存在的数据，缓存与 DB 都没有 | 请求全部打到 DB | 空值缓存（短 TTL）、布隆过滤器、参数校验 |
| 击穿 | 某个热 key 突然过期 | 大量请求同时回源 | 互斥锁重建、逻辑过期、热点永不过期 |
| 雪崩 | 大批 key 同时过期或 Redis 宕机 | DB 瞬间被打挂 | TTL 加随机、多级缓存、限流降级、集群 |

本项目 G1 次 2026-04-15 二等座就是"热 key"：库存 key 被 100 并发同时读写。我们的做法是 Redis + 分布式锁 + DB 兜底三层配合（见 4.2.7），即使缓存错误也有 DB 的 `remaining_count >= 1` 最后防线。

**怎么排错**

| 现象 | 判断 | 处理 |
| --- | --- | --- |
| DB QPS 突然飙升、缓存命中率骤降 | 疑似穿透/击穿 | 看 key 是否存在：不存在→穿透；单个热 key→击穿 |
| Redis 监控大量 key 同一秒过期 | 雪崩前兆 | TTL 加随机抖动 |
| 空值被缓存后数据又出现了 | 空值 TTL 太长 | 空值 TTL 设短并主动删除 |

**示例代码**

```java
String cache = redis.opsForValue().get(key);
if (cache != null) {
    return cache.isEmpty() ? null : Integer.parseInt(cache);   // 空串 = 已缓存的空值
}
Integer fromDb = loadFromDb(key);
if (fromDb == null) {
    redis.opsForValue().set(key, "", 60, TimeUnit.SECONDS);    // 空值缓存，短 TTL 防穿透
    return null;
}
redis.opsForValue().set(key, String.valueOf(fromDb), 60, TimeUnit.SECONDS);
return fromDb;
```

要点：空值缓存必须写"空串"而不是不写，读取时要先判空串再 `parseInt`，否则第一次穿透会永远打库、空串还会触发 `NumberFormatException`。

### 4.2.4 分布式锁演进：从 SETNX 到 Redisson

四代方案，面试按这个顺序讲：

```text
第一代: SETNX key 1                          → 宕机死锁
第二代: SETNX + EXPIRE                        → 两条命令非原子
第三代: SET key value NX PX 30000             → 原子加锁，但误删/续期未解决
第四代: 唯一 value + Lua 校验删除 + 看门狗续期   → 即 Redisson 思路
```

锁过期、误删、续期三个高频问题的标准答案：

| 问题 | 场景 | 解决 |
| --- | --- | --- |
| 锁过期 | 业务执行超过 TTL，锁自动释放，别人拿到锁 | 看门狗自动续期；合理评估 TTL；业务幂等 |
| 锁误删 | A 的锁过期后被 B 拿到，A 执行完直接 DEL 删了 B 的锁 | value 存唯一持有者标识，Lua 先校验再删除 |
| 续期 | 长业务需要锁续命 | 后台定时任务每 TTL/3 续一次，直到释放 |

释放锁的 Lua（本项目 `RedisDistributedLockManager` 同款思路）：

```lua
if redis.call('hexists', KEYS[1], ARGV[2]) == 0 then
    return -1
end
local holdCount = redis.call('hincrby', KEYS[1], ARGV[2], -1)
if holdCount > 0 then
    redis.call('pexpire', KEYS[1], ARGV[1])
    return holdCount
end
redis.call('del', KEYS[1])
return 0
```

**怎么排错**

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 报错 `attempt to compare...` | 锁 key 类型不对（被其他结构占用） | `TYPE` 检查，规范 key |
| 业务偶发重复执行 | 锁提前过期 | 加续期或延长 TTL，业务侧幂等 |
| 明明加了锁仍超卖 | 锁没覆盖全部扣减路径，或锁 key 粒度不一致 | 统一 lock key（车次+日期+席别） |

### 4.2.5 Redisson 可重入锁原理与本次实现对照

Redisson `RLock` 的加锁 Lua 做了三件事：

```lua
-- 1. 锁不存在：创建 Hash，field=客户端ID:线程ID，value=1，设置过期
-- 2. 锁存在且 field 是当前线程：重入次数 +1，刷新过期
-- 3. 其他情况：返回剩余 TTL，客户端订阅锁释放消息等待
```

解锁时重入次数 -1，减到 0 才 `del`。看门狗（watchdog）默认 30s TTL，每 10s 续期一次。

本项目 `RedisDistributedLockManager` 与 Redisson 的对照：

| 能力 | Redisson | 本项目实现 |
| --- | --- | --- |
| 可重入 | Hash field 计数 | Hash field=clientId:threadId 计数 |
| 防误删 | 校验 field 后删除 | Lua 校验 `hexists` 后删除 |
| 续期 | watchdog 后台续期 | `ScheduledExecutorService` 每 TTL/3 续期 |
| 等待唤醒 | pub/sub 通知 | 50ms 轮询重试（学习版简化） |
| 内存兜底 | 无 | `InMemoryDistributedLockManager` 同接口 |

内存兜底实现的关键点与 Redis 版本一一对应：`owner=Thread` 对应 field，`holdCount` 对应 Hash value，`expireAt` 对应 PEXPIRE，`release` 校验 owner 对应 Lua 校验 field。

### 4.2.6 Lua 原子操作：为什么要 Lua

**是什么**

Redis 单线程执行命令，Lua 脚本整体作为一个命令执行，期间不会插入其他命令。`EVAL script numkeys key... arg...` 执行，`EVALSHA` 按 SHA1 复用脚本。

**为什么**

"读库存 → 判断 → 扣减"三步如果分开执行，并发下会穿插其他请求导致超卖：

```text
A: GET stock = 1
B: GET stock = 1
A: 1>=1 → DECR → 0
B: 1>=1 → DECR → -1     ← 超卖
```

用 Lua 打包后：

```lua
local stock = redis.call('get', KEYS[1])
if stock == false then
    return -1
end
if tonumber(stock) < tonumber(ARGV[1]) then
    return 0
end
redis.call('decrby', KEYS[1], ARGV[1])
return 1
```

返回值约定：`-1` 未预热，`0` 库存不足，`1` 扣减成功。

**怎么排错**

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| `NOSCRIPT` | 脚本未加载就 EVALSHA | 客户端自动回退 EVAL（Spring 已处理） |
| 脚本执行慢拖垮 Redis | 脚本里有大循环/大 key | 脚本保持 O(1)，控制单次处理量 |
| 参数类型不对 | tonumber 失败返回 nil | 入参显式转字符串数字 |

### 4.2.7 Redis 与数据库一致性：本章的取舍

库存权威数据仍在 MySQL `seat_inventory`，Redis 只做"快速预检 + 削峰"：

```text
Redis扣减成功 → DB事务下单(FOR UPDATE + remaining_count>=1) → 成功则结束
                                        │ 失败
                                        ▼
                              Redis回补 compensate +1
```

| 风险 | 说明 | 兜底 |
| --- | --- | --- |
| Redis 扣了、DB 没扣 | DB 下单失败 | 捕获异常后 `compensate` 回补 |
| DB 扣了、Redis 没扣 | 极难发生（顺序上 Redis 先） | 定时对账重新预热 |
| 进程崩溃 | Redis 扣减后、DB 提交前宕机 | 缓存设 TTL；定时任务以 DB 为准重新预热；业务幂等 |
| 缓存与 DB 短暂不一致 | 并发读写 | 接受最终一致，第6章候补分析定时校正 |

这也是面试标准答法：**缓存不追求强一致，追求最终一致；超卖的最终防线永远在数据库 `remaining_count >= 1`**。第3章的 DB 防线原样保留，Redis 只是把压力挡在前面。

---

## 4.3 消息队列与削峰 L3

### 4.3.1 为什么需要 MQ：同步 vs 异步

同步下单：HTTP 线程全程等待锁、Redis、DB，1000 QPS 时需要上千工作线程，DB 被瞬时流量打穿。

异步下单：HTTP 只把请求塞进队列就返回"已受理"，消费者按自己的节奏落库，流量高峰被队列"削"成平稳的消费曲线。

```text
同步: 用户 → [锁+库存+DB 200ms] → 响应
异步: 用户 → [入队 1ms] → 响应(排队位次)
              └→ 队列 → 4消费者 → 锁+库存+DB → 轮询结果
```

三个价值：**异步**（响应快）、**削峰**（保护 DB）、**解耦**（下单与库存、短信、通知各自订阅）。

**怎么排错**

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 队列无限堆积 | 消费能力 < 生产速度 | 扩容消费者、限流生产者、队列设上限快速失败 |
| 消息丢失 | 队列内存态、进程崩溃 | 持久化、ACK 机制、本地消息表 |
| 重复消费 | 重试/网络抖动 | 消费幂等（requestId/orderNo 唯一键） |

### 4.3.2 Kafka 与 RocketMQ 核心概念

| 概念 | Kafka | RocketMQ |
| --- | --- | --- |
| 元数据 | ZooKeeper/KRaft | NameServer |
| 存储单元 | Topic-Partition | Topic-MessageQueue |
| 消费位点 | Offset（客户端/组协调） | Offset（Broker 管理） |
| 顺序消息 | 分区内有序 | 队列内有序 |
| 事务消息 | 支持（幂等+事务） | 支持（半消息+回查） |
| 延迟消息 | 时间轮（5.x 支持） | 内置 18 个延迟级别 |
| 典型场景 | 日志、大数据管道 | 电商交易、订单 |

抢票场景推荐 RocketMQ：事务消息天然适合"扣库存-下单"一致性；Kafka 更适合第6章 AI 分析日志管道。

**怎么排错**

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 消费堆积 | 消费者组数量/线程不足 | 增加消费者实例（不超过分区数） |
| 重复消费 | 位点提交失败 | 幂等消费 |
| 消息顺序乱 | 多分区并发消费 | 按订单号 hash 到同一分区 |

### 4.3.3 虚拟排队与限流

虚拟排队 = 有界队列 + 排队位次。本项目做法：

1. 队列容量 `queue-capacity: 200`，满了直接返回业务码 2003"抢票队列已满"；
2. 入队成功返回 `requestId` 与近似 `queuePosition`（入队时 `size()+1`）；
3. 前端用 `GET /api/order/grab/{requestId}` 轮询结果，避免长连接。

限流的补充手段：令牌桶（Guava RateLimiter）控制入队速率、Sentinel 熔断、Nginx 层 `limit_req`。第7章再补 Nginx 与监控。

### 4.3.4 异步下单的一致性：幂等、补偿、本地消息表

| 手段 | 说明 | 本项目落地 |
| --- | --- | --- |
| 幂等 | 同一请求重复消费只生成一单 | requestId 唯一；DB 模式可用 orderNo 唯一索引 |
| 补偿 | 失败步骤反向操作 | DB 下单失败回补 Redis 库存 |
| 本地消息表 | 业务与消息同事务写本地表，再投递 | 第7章/补充专题 Seata 时实现 |
| 事务消息 | 半消息 + 回查确认 | RocketMQ 生产方案，文档给接入骨架 |
| 对账 | 定时比对缓存与 DB | 定时重新预热库存 |

生产级流程（RocketMQ）建议：先落"抢票请求表"（状态=待处理），再发消息；消费者写订单成功后更新请求状态；定时任务扫描超时未完成请求做补偿。本项目内存版保留了同样的状态机（QUEUED → SUCCESS/FAILED）。

### 4.3.5 真实接入 Kafka/RocketMQ 步骤（本机不执行）

以 RocketMQ 为例：

1. `pom.xml` 增加依赖：

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
    <version>2.3.1</version>
</dependency>
```

2. `application.yml` 配置：

```yaml
rocketmq:
  name-server: localhost:9876
  producer:
    group: railway-grab-producer
```

3. 发送方把 `GrabQueue.submit` 替换为 `rocketMQTemplate.convertAndSend("grab-topic", message)`；
4. 消费方用 `@RocketMQMessageListener(topic = "grab-topic", consumerGroup = "grab-consumer")` 替代 `GrabQueue` 的消费循环，处理逻辑复用 `GrabProcessor`；
5. 结果仓库换成 Redis Hash（`railway:grab:result:{requestId}`）或 DB 表，`GrabResultRepository` 接口不变。

Kafka 同理：`spring-kafka` + `KafkaTemplate` + `@KafkaListener`，下单逻辑完全复用。

---

## 4.4 实战一：Redis 分布式锁

### 4.4.1 代码结构

| 文件 | 职责 |
| --- | --- |
| `service/concurrency/DistributedLock.java` | 锁接口 `tryLock(wait, lease) / unlock` |
| `service/concurrency/DistributedLockManager.java` | 锁工厂 `getLock(key)` |
| `service/concurrency/RedisDistributedLockManager.java` | Spring StringRedisTemplate + 三段 Lua + 看门狗 |
| `service/concurrency/InMemoryDistributedLockManager.java` | 内存兜底：owner + 重入计数 + 过期时间 |
| `config/GrabConfig.java` | 按 `railway.redis.mode` 装配实现 |

### 4.4.2 Redis 加锁 Lua（可重入）

```lua
if redis.call('exists', KEYS[1]) == 0 then
    redis.call('hincrby', KEYS[1], ARGV[2], 1)
    redis.call('pexpire', KEYS[1], ARGV[1])
    return 1
end
if redis.call('hexists', KEYS[1], ARGV[2]) == 1 then
    redis.call('hincrby', KEYS[1], ARGV[2], 1)
    redis.call('pexpire', KEYS[1], ARGV[1])
    return 1
end
return 0
```

- KEYS[1]：锁 key；ARGV[1]：TTL 毫秒；ARGV[2]：持有者 `clientId:threadId`；
- 返回 1 抢到，0 被占用；同线程重入只加计数不重置持有者。

### 4.4.3 看门狗续期

```java
renewTask = renewExecutor.scheduleWithFixedDelay(this::renew, leaseMillis / 3, leaseMillis / 3, TimeUnit.MILLISECONDS);
```

每 TTL/3 执行续期 Lua，校验 field 后 `pexpire`。释放时取消任务。

### 4.4.4 锁测试结果（JUnit5 真实运行）

| 测试 | 场景 | 结果 |
| --- | --- | --- |
| `lockShouldSerializeIncrement` | 20 线程各 500 次加锁自增 | 恰好 10000，无丢失 |
| `sameThreadShouldAcquireReentrantly` | 同线程重入两次再释放两次 | 通过，其他线程可再获取 |
| `expiredLockShouldBePreempted` | TTL 50ms 过期后被抢占 | 通过（模拟宕机自动释放） |
| `waitTimeoutShouldReturnFalseWhileHeld` | 锁被占用时等待 100ms | 返回 false，未误抢 |

### 4.4.5 真实 Redis 接入与验证

1. 本机安装 Redis 后启动 `redis-server`（或用已有实例）；
2. `application.yml` 设置 `railway.redis.mode: redis`（Redis 连接参数已配好 `spring.data.redis`，默认 localhost:6379）；
3. 重启应用，`SET`/`HSET` 观察：

```bash
redis-cli -h localhost -p 6379
> TYPE railway:lock:grab:G1:2026-04-15:二等座     # hash
> HGETALL railway:lock:grab:G1:2026-04-15:二等座  # field=clientId:threadId
> TTL railway:lock:grab:G1:2026-04-15:二等座      # 续期后仍在
```

4. 再次执行 `scripts/grab-stress.ps1` 验证真实 Redis 路径。切换到 Redis 后若 Redis 不可用，抢票会返回"获取分布式锁超时"，此时应回退 `memory` 模式。

---

## 4.5 实战二：库存扣减与抢票接口

### 4.5.1 接口定义

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/order/grab?sync=false` | 异步抢票，立即返回 requestId 与排队位次 |
| POST | `/api/order/grab?sync=true` | 同步抢票，直接返回 SUCCESS/FAILED |
| GET | `/api/order/grab/{requestId}` | 查询异步结果 |
| GET | `/api/order/grab/stats` | 压测统计：submitted/success/failed/orders/QPS |
| GET | `/api/order/grab/stock` | 查询库存缓存 |
| POST | `/api/order/grab/stock/warmup` | 预热库存（生产可从 DB 定时同步） |
| POST | `/api/order/grab/reset` | 重置内存模式数据（压测用） |

### 4.5.2 请求与响应示例

```bash
curl -X POST "http://localhost:8080/api/order/grab" \
     -H "Content-Type: application/json" \
     -d '{"userId":9001,"trainNo":"G1","travelDate":"2026-04-15","fromStation":"北京南","toStation":"上海虹桥","seatType":"二等座","passengerName":"张三"}'
```

异步受理响应（实测）：

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

结果查询（实测）：

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "requestId": "4b4490a561ff4b59bb0860f7a2c0393d",
    "status": "SUCCESS",
    "code": 0,
    "message": "抢票成功",
    "orderNo": "GRAB202609151352520300041",
    "price": 0,
    "queuePosition": 1,
    "submittedAtMillis": 1789451572301,
    "finishedAtMillis": 1789451572303,
    "costMillis": 2
  }
}
```

同步模式与失败示例（实测）：

| 请求 | 响应 status | message |
| --- | --- | --- |
| sync 第 1 次（库存1） | SUCCESS | 抢票成功 |
| sync 第 2 次（库存0） | FAILED | 余票不足，抢票失败 |
| 库存未预热 | FAILED | 库存尚未预热，请联系管理员 |
| DB 下单异常 | FAILED | 模拟下单失败（库存已回补） |

### 4.5.3 扣减顺序与三道防线

```text
第1道 分布式锁        串行化同一车次+日期+席别的扣减
第2道 Redis/Lua 扣减  库存不足直接返回，不触达 DB
第3道 DB 事务          FOR UPDATE + remaining_count>=1（第3章保留）
```

即使 Redis 库存被预热成错误值（如 100），DB 层仍会拒绝超卖；反之 Redis 说没票时也不会压到 DB。

---

## 4.6 实战三：内存 MQ 异步下单

`GrabQueue` 用 30 行实现了最小可用的 MQ：

```java
public boolean submit(GrabMessage message) {
    return queue.offer(message);          // 有界队列，满了返回 false
}

private void consumeLoop() {
    while (running) {
        GrabMessage message = queue.poll(200, TimeUnit.MILLISECONDS);
        if (message == null) {
            continue;
        }
        GrabResult result = processor.process(message);
        completionHandler.accept(result);  // 写回结果仓库
    }
}
```

与真实 MQ 的映射关系：

| 内存实现 | Kafka/RocketMQ 对应 |
| --- | --- |
| `ArrayBlockingQueue` | Topic/Partition 的 Broker 端队列 |
| `submit` | Producer `send` |
| `consumeLoop` | Consumer 拉取循环 / `@KafkaListener` |
| `completionHandler` | 消费位点提交 + 业务落库 |
| 队列满返回 2003 | 生产端限流/队列水位告警 |
| `shutdown()` | 消费者优雅下线 |

验证结果：异步 100 请求，4 消费者，10 库存，恰好 10 成功、90 失败、10 订单、库存 0，队列最终清空（`queueSize=0`）。

---

## 4.7 压测与验收

### 4.7.1 JUnit5 并发测试（12 个用例全部通过）

```text
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
- RailwayApplicationTests        1（上下文加载）
- DistributedLockTest            4（互斥/重入/过期/超时）
- GrabConcurrencyTest            5（同步/异步/未预热/回补/队满）
- NonAtomicDeductTest            2（无锁丢失更新/CAS正确）
```

关键用例实测数字：

| 用例 | 并发/库存 | 结果 |
| --- | --- | --- |
| 同步抢票 | 100/10 | success=10，failed=90，orders=10，remaining=0 |
| 异步抢票（4消费者） | 100/10 | success=10，failed=90，orders=10，remaining=0 |
| 无锁 check-then-act | 100/10 | "success"=100，stock=9（丢失99次更新） |
| CAS 原子扣减 | 100/10 | success=10，stock=0 |
| DB 下单失败 | 1/10 | status=FAILED，库存回补为 10，订单 0 |
| 队列已满 | 容量1 | 业务码 2003，rejected=1 |

### 4.7.2 PowerShell HTTP 压测（100 并发抢 10 张票）

运行命令：

```powershell
.\scripts\grab-stress.ps1 -Mode sync  -Concurrency 100 -Stock 10 -ResultFile target\grab-stress-sync.txt
.\scripts\grab-stress.ps1 -Mode async -Concurrency 100 -Stock 10 -ResultFile target\grab-stress-async.txt
```

实测结果：

| 指标 | sync 同步 | async 异步 |
| --- | --- | --- |
| 请求受理成功 | 100 | 100 |
| 提交耗时 | 101 ms | 76 ms |
| 提交 QPS | 990.1 | 1315.8 |
| 端到端耗时 | 101 ms | 82 ms |
| 端到端 QPS | 990.1 | 1219.5 |
| 平均受理耗时 | 1.01 ms | 0.76 ms |
| 服务端 success | 10 | 10 |
| 服务端 failed | 90 | 90 |
| 订单数 | 10 | 10 |
| 剩余库存 | 0 | 0 |

说明：本机未装 JMeter/wrk，以上为内存模拟模式（Redis 锁、库存、订单均走内存实现）的真实 HTTP 压测，客户端为 PowerShell HttpClient 100 并发。应用启动耗时约 4.0s，压测期间日志 0 条 ERROR。切换到真实 Redis/MySQL 后执行同一脚本即可对比。

### 4.7.3 验收标准逐条对照

| 验收标准 | 结果 |
| --- | --- |
| 100 并发抢 10 张票，恰好生成 10 个订单，不多不少 | ✅ JUnit 同步/异步 + HTTP 压测三处验证均为 10 |
| 能写 Redis 分布式锁，并说清锁过期、误删、续期问题 | ✅ 4.2.4/4.4；Redis 版与内存版代码均落地 |
| 能解释 MQ 削峰原理，说明异步下单一致性如何处理 | ✅ 4.3；补偿+幂等+最终一致方案 |
| 能解释 i++ 为什么不安全，CAS 和 synchronized 的区别 | ✅ 4.1.2/4.1.5，并用测试复现丢失更新 |

---

## 4.8 自检三问与面试高频

### 自检三问

1. **是什么**：分布式锁用 `SET NX PX` + Lua 校验持有者；库存扣减用 Lua 把"读-判断-扣"变成原子操作；MQ 用有界队列把瞬时流量削成平稳消费。
2. **为什么**：1000 QPS 下 DB 行锁与连接池会先成为瓶颈，必须把判定前移到 Redis；但缓存不能保证强一致，所以 DB 的 `remaining_count >= 1` 永远是最后防线。
3. **怎么排错**：抢票失败先看 `GET /api/order/grab/{requestId}` 的 code（2001 余票、2002 下单、2003 队满、2004 请求不存在）；锁问题看锁 key 类型与持有者；压测问题看 stats 的 queueSize 与 rejected。

### 高频面试题速答

| 问题 | 答题要点 |
| --- | --- |
| 分布式锁误删怎么办 | value 存唯一持有者，Lua 先校验 field 再删；见 4.2.4 |
| 锁过期业务没执行完 | 看门狗每 TTL/3 续期；业务评估 TTL；最终靠 DB 兜底 |
| i++ 为什么线程不安全 | 读-改-写三步非原子；volatile 也不行；用原子类/锁 |
| CAS 和 synchronized 区别 | CAS 无锁自旋乐观失败重试；synchronized 悲观挂起阻塞；低竞争用 CAS，高竞争用锁 |
| AQS 原理 | volatile state + CLH 队列，独占/共享/条件模式，模板方法 |
| 缓存穿透击穿雪崩 | 空值+布隆；互斥重建+逻辑过期；TTL随机+多级+熔断 |
| MQ 削峰原理 | 生产者入队即返回，消费者按能力消费，峰值被队列吸收 |
| 异步下单一致性 | 本地消息表/事务消息+幂等+补偿+对账，最终一致 |
| 1000 QPS 怎么扛 | Redis 预检+Lua、MQ 削峰、无锁 CAS、连接池与线程池参数、DB 最终兜底 |

---

## 4.9 本章代码地图

| 文件 | 职责 |
| --- | --- |
| `src/main/java/com/railway/service/concurrency/DistributedLock.java` | 锁接口 |
| `.../DistributedLockManager.java` | 锁管理器接口 |
| `.../RedisDistributedLockManager.java` | Redis 可重入锁 + Lua + 看门狗 |
| `.../InMemoryDistributedLockManager.java` | 内存兜底锁（模拟验证） |
| `.../StockStore.java` + `InMemoryStockStore.java` + `RedisStockStore.java` | 库存扣减抽象与两种实现 |
| `.../GrabKeys.java` | 锁 key / 库存 key 统一生成 |
| `.../GrabProperties.java` | `railway.grab.*` 参数绑定 |
| `.../GrabMessage.java` / `GrabResult.java` / `GrabResultRepository.java` | 消息与结果状态机 |
| `.../GrabOrderWriter.java` + `DbGrabOrderWriter.java` + `InMemoryGrabOrderWriter.java` | 下单写入抽象（DB/内存） |
| `.../GrabProcessor.java` | 锁→扣减→下单→补偿 核心流水线 |
| `.../GrabQueue.java` | 内存 MQ + 消费者 |
| `.../GrabService.java` | 抢票服务：提交、查询、统计、预热、清理 |
| `src/main/java/com/railway/controller/GrabController.java` | `POST /api/order/grab`（sync/async 同一路径）等 6 个接口 |
| `src/main/java/com/railway/service/OrderRequestValidator.java` | 下单/抢票共用参数校验 |
| `src/main/java/com/railway/config/GrabConfig.java` | 双模式条件装配 |
| `src/test/java/com/railway/service/concurrency/*` | 11 个并发测试（连同上下文加载共 12 个测试） |
| `scripts/grab-stress.ps1` | PowerShell 100 并发压测脚本 |

---

## 4.10 踩坑记录

| 坑 | 现象 | 原因 | 修复 |
| --- | --- | --- | --- |
| 内存锁 map 移除竞态 | 日志大量"锁不属于当前线程" | 释放后 `remove`，等待线程持有被移除的 entry | 锁 entry 不再移除，仅清 owner |
| 压测 reset 无效 | 统计从 200 开始 | 脚本用了 GET，接口是 POST，405 被当成 JSON 解析 | 增加 POST 辅助函数并校验 code |
| PowerShell 中文乱码/解析失败 | 脚本直接语法报错 | PS5.1 需要 UTF-8 BOM | 脚本保存为 UTF-8 with BOM |
| `GrabOrderWriter` 不能写成 lambda | 编译报"不是函数式接口" | 接口有 4 个抽象方法 | 测试中用匿名类实现 |
| 无 MySQL 时启动失败风险 | DataSource 初始化阻塞 | Hikari 默认 fail-fast | `initialization-fail-timeout: -1`，DB 不可用时应用仍可启动（抢票走内存模式） |
| 异步结果被 QUEUED 覆盖 | 理论上可能结果回退 | 先 markSubmitted 再入队，消费者完成晚于入队 | 已按"先登记再入队"顺序实现 |

---

## 4.11 下一章预告

**第5章 中转换乘路径规划与站内规则引擎**：本章的高并发基础设施就位后，下一章进入算法专题：邻接表 + 优先队列 + Dijkstra/A* + 时间扩展图，实现"最快/最经济"换乘方案；再叠加广州南站 500 条规则简化版，用策略模式 + 责任链输出检票口与走行路线。
