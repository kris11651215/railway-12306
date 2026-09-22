# 第2章 MySQL数据库设计与SQL实战

> 项目：铁路智慧出行综合服务平台
> 状态：✅ 已完成
> 建议耗时：第3-4周前段，约4-5天
> 深度：L4（索引、事务、锁必须懂原理，能排错）
> 一句话：把第1章的实体落成七张表，用索引、事务与锁撑起"余票不失真、抢票不超卖"。

---

## 本章目标

| 目标 | 具体产出 | 验收方式 |
| --- | --- | --- |
| 表结构落地 | 七张表 + 建表 SQL | `sql/init.sql` 可一键导入 |
| 索引与优化 | B+树/最左前缀/覆盖索引/回表 | 能用 EXPLAIN 看出全表扫描并加对索引 |
| 事务与锁 | ACID/隔离级别/MVCC/行锁/间隙锁/死锁 | 能解释可重复读下的幻读 |
| 席位复用 | 原子区段库存模型 | 说清区段起点站序/终点站序的作用 |
| 聚合查询 | 候补按站+日期聚合 SQL | SQL 结果能作为加开建议的输入 |

配套材料：

- 建表脚本与样例数据：`sql/init.sql`
- 数据库设计文档（含完整 EXPLAIN 实测）：`docs/architecture/01-database-design.md`

---

## 2.0 实验环境

```bash
mysql -u root -p --default-character-set=utf8mb4 < sql/init.sql
```

导入后核对：

```sql
USE railway;
SELECT COUNT(*) FROM station;         -- 13
SELECT COUNT(*) FROM train;           -- 9
SELECT COUNT(*) FROM train_station;   -- 33
SELECT COUNT(*) FROM seat_inventory;  -- 947（含批量生成数据）
SELECT COUNT(*) FROM candidate;       -- 3025（含批量生成数据）
```

本章所有 SQL 均可在导入后的库中直接执行，EXPLAIN 输出为 MySQL 8.4.0 实测。

---

## 2.1 建表与 CRUD

### 2.1.1 建表三要素

**是什么**：一张合格的表 = 正确的字段类型 + 约束（主键/唯一/外键/CHECK）+ 注释。

**为什么**：类型决定存储与计算精度（金额用 DECIMAL），约束把业务规则下沉到数据库（余票不能为负），注释让半年后的自己看得懂。

**怎么排错**：

| 报错 | 原因 | 处理 |
| --- | --- | --- |
| ERROR 1064 | SQL 语法错误，常因用了保留字（order/rank） | 改表名或加反引号 |
| ERROR 1452 | 外键约束失败，父表无对应记录 | 先插父表，再插子表 |
| ERROR 1406 | 数据超长 | 扩大字段或校验输入 |
| ERROR 3819 | CHECK 约束不满足 | 检查 remaining_count 等业务规则 |

**示例 SQL**（库存表的完整建表片段）：

```sql
CREATE TABLE seat_inventory (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    train_id           BIGINT UNSIGNED NOT NULL,
    travel_date        DATE            NOT NULL,
    seat_type          VARCHAR(20)     NOT NULL,
    from_station_order INT             NOT NULL,
    to_station_order   INT             NOT NULL,
    total_count        INT             NOT NULL,
    remaining_count    INT             NOT NULL,
    price              DECIMAL(10, 2)  NOT NULL,
    version            INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_segment (train_id, travel_date, seat_type, from_station_order, to_station_order),
    CONSTRAINT fk_inv_train FOREIGN KEY (train_id) REFERENCES train (id),
    CONSTRAINT chk_segment CHECK (to_station_order > from_station_order),
    CONSTRAINT chk_remaining CHECK (remaining_count >= 0)
) ENGINE = InnoDB COMMENT = '座位库存表';
```

### 2.1.2 CRUD 四板斧

```sql
-- 增：新增一个车站
INSERT INTO station (station_code, station_name, city, bureau, affiliated_depot, station_class, is_hub)
VALUES ('TSP', '测试站', '测试市', '测试局集团', '测试动车段', '二等站', 0);

-- 查：按路局查车站，按站名模糊查（前导确定才能用索引）
SELECT station_name, bureau FROM station WHERE bureau = '广铁集团';
SELECT station_name FROM station WHERE station_name LIKE '广州%';

-- 改：修改站等级
UPDATE station SET station_class = '一等站' WHERE station_name = '测试站';

-- 删：删除测试站
DELETE FROM station WHERE station_name = '测试站';
```

### 2.1.3 JOIN 与聚合

```sql
-- 查 G1 的全部经停（JOIN 经停表与车站表）
SELECT ts.station_order, s.station_name, ts.arrival_time, ts.departure_time, ts.mileage_from_start
FROM train_station ts
         JOIN station s ON s.id = ts.station_id
         JOIN train t ON t.id = ts.train_id
WHERE t.train_no = 'G1'
ORDER BY ts.station_order;

-- 聚合：各席别库存总量
SELECT seat_type, SUM(total_count) AS total, SUM(remaining_count) AS remaining
FROM seat_inventory
GROUP BY seat_type;
```

---

## 2.2 B+ 树：索引为什么快

**是什么**：InnoDB 索引底层是 B+ 树——多叉平衡树，非叶子节点只存键值和页指针，叶子节点存数据并按序用双向链表相连。一次查询从根走到叶，通常 3~4 次磁盘 IO 就能在千万级数据中定位记录。

**为什么**：

- 与二叉搜索树相比，多叉树把树高压到 3~4 层，大幅减少磁盘 IO；
- 叶子链表让范围查询（`BETWEEN`、`>=`）天然高效，不用回到根节点重新走；
- 与哈希索引相比，B+ 树支持排序、范围、最左前缀，通用性更强。

一些量化直觉：

| 概念 | 数值 |
| --- | --- |
| InnoDB 页大小 | 16KB |
| 非叶子节点可容纳键值 | 约上千个 |
| 三层 B+ 树可存数据 | 约 2000 万行 |
| 点查磁盘 IO | 约 3~4 次 |

**怎么排错**：怀疑索引没生效时，先 `EXPLAIN` 看 `type` 和 `key`；`EXPLAIN ANALYZE`（MySQL 8.0.18+）可以看到实际执行时间与行数。

**示例 SQL**：

```sql
EXPLAIN SELECT * FROM candidate WHERE travel_date = '2026-04-15';
```

---

## 2.3 聚簇索引、二级索引、回表与覆盖索引

**是什么**：

- **聚簇索引**：主键索引，叶子节点存放整行数据。InnoDB 表本身按主键组织；
- **二级索引**：叶子节点存放"索引列 + 主键值"，要拿其他列必须用主键再查一次聚簇索引，这就是**回表**；
- **覆盖索引**：查询需要的列全部包含在索引里，无需回表，EXPLAIN 的 Extra 会显示 `Using index`。

**为什么**：回表意味着每行多一次 B+ 树点查，行数一大代价成倍增长。能用覆盖索引的查询，尽量把 SELECT 列控制在索引范围内，或者调整联合索引的列顺序。

**怎么排错**：EXPLAIN 时重点看 Extra：出现 `Using index` 表示覆盖；只有 `Using index condition` 说明虽然下推了条件但还要回表。

**示例 SQL**（本项目实测，详见设计文档 3.4/3.5）：

```sql
-- 覆盖索引：四列条件都在 idx_agg(travel_date, from_station_id, to_station_id, status) 中
EXPLAIN SELECT COUNT(*) FROM candidate
WHERE travel_date = '2026-04-15' AND from_station_id = 8 AND to_station_id = 12 AND status = 'WAITING';
-- Extra: Using where; Using index

-- 回表：candidate_no 不在索引中，需要回表
EXPLAIN SELECT candidate_no FROM candidate
WHERE travel_date = '2026-04-15' AND from_station_id = 8 AND to_station_id = 12 AND status = 'WAITING';
-- Extra: Using index condition（没有 Using index）
```

---

## 2.4 最左前缀原则与联合索引设计

**是什么**：联合索引 `(a, b, c)` 像电话簿先按 a 排、a 相同再按 b 排。查询想用索引，必须从最左列开始连续匹配；中间断了，后面的列只能退化为过滤。

**为什么**：B+ 树只有一种排序方式。跳过 a 直接查 b，就像跳过姓氏直接按名字找电话，索引树用不上。

**怎么排错**：

| 查询条件 | 能否用 idx_agg(travel_date, from, to, status) | 说明 |
| --- | --- | --- |
| travel_date = ? | 能 | 最左列 |
| travel_date = ? AND from = ? | 能 | 连续前缀 |
| from = ? AND to = ? | 不能 | 缺最左列 |
| travel_date = ? AND to = ? | 部分 | 只能用 travel_date，to 需回索引过滤 |

**示例 SQL**：

```sql
-- 能用：最左两列连续
EXPLAIN SELECT COUNT(*) FROM candidate WHERE travel_date = '2026-04-15' AND from_station_id = 8;

-- 不能用 idx_agg：缺少最左列（实测走了 FK 索引合并）
EXPLAIN SELECT COUNT(*) FROM candidate WHERE from_station_id = 8 AND to_station_id = 12;
```

**联合索引列序口诀**：等值在前、范围在后、排序分组放最后；区分度高的列尽量靠前（但最左列优先满足高频等值条件）。

---

## 2.5 EXPLAIN 完全解读

**是什么**：`EXPLAIN` 输出优化器的执行计划，不改数据。核心列如下：

| 列 | 含义 | 重点看什么 |
| --- | --- | --- |
| type | 访问类型 | ALL（全表）< index < range < ref < eq_ref < const |
| possible_keys | 可能用到的索引 | 为 NULL 说明没有可用索引 |
| key | 实际使用的索引 | 为 NULL 就是没用索引 |
| key_len | 使用索引的字节数 | 判断联合索引用了几列 |
| rows | 预估扫描行数 | 越小越好 |
| filtered | 过滤后剩余比例 | 结合 rows 判断真实代价 |
| Extra | 附加信息 | Using index（覆盖）、Using filesort（额外排序）、Using temporary（临时表）都是优化信号 |

**为什么**：MySQL 优化器基于成本选择计划，并不总是选你以为的索引，EXPLAIN 是唯一的"解释权"。

**怎么排错**：先看 type 是不是 ALL；再看 key 是否命中；再看 rows 是否明显偏大；最后看 Extra 有没有 filesort/temporary。复杂 SQL 用 `EXPLAIN FORMAT=JSON` 或 `EXPLAIN ANALYZE`。

**示例 SQL**（候补聚合，实测前后对比）：

```sql
EXPLAIN
SELECT c.travel_date, fs.station_name, ts.station_name, c.seat_type, COUNT(*)
FROM candidate c
         JOIN station fs ON fs.id = c.from_station_id
         JOIN station ts ON ts.id = c.to_station_id
WHERE c.status = 'WAITING' AND c.travel_date = '2026-04-15'
GROUP BY c.travel_date, fs.station_name, ts.station_name, c.seat_type;
```

实测：加索引前 `type=ALL, rows=3001, Extra: Using temporary; Using filesort`；加 `idx_agg` 后 `type=ref, key=idx_agg, rows=1018, Extra: Using index condition`。完整输出见设计文档第 3 节。

---

## 2.6 索引失效的常见场景

**是什么**：明明有索引，EXPLAIN 却是 `type=ALL`、`key=NULL`。

**为什么**：索引是对列值的**有序结构**，一旦表达式破坏了"按列值可比"的前提，B+ 树就用不上。

**怎么排错**：对照下表逐条排查。

| 场景 | 失效示例 | 修正写法 |
| --- | --- | --- |
| 函数包列 | `WHERE DATE(candidate_time)='2026-04-15'` | `WHERE candidate_time >= '2026-04-15' AND candidate_time < '2026-04-16'` |
| 运算包列 | `WHERE train_id + 1 = 8` | `WHERE train_id = 7` |
| 前导模糊 | `WHERE station_name LIKE '%南'` | 尽量 `LIKE '广州%'`；全文需求上 ES |
| 隐式类型转换 | `WHERE station_code = 123`（列为字符串） | `WHERE station_code = '123'` |
| OR 连接不同列 | `WHERE a=1 OR b=2` | 拆成 UNION 或建对应联合索引 |
| 违反最左前缀 | `WHERE to_station_id = 12` | 带上 `travel_date` 等左侧列 |
| 优化器放弃 | 小表或全表本来就便宜 | 看成本，不迷信索引 |

**示例 SQL**（实测均 `type=ALL, key=NULL`）：

```sql
EXPLAIN SELECT * FROM candidate WHERE DATE(candidate_time) = '2026-04-15';
EXPLAIN SELECT * FROM station WHERE station_name LIKE '%南';
```

---

## 2.7 事务与 ACID

**是什么**：事务是一组要么全成功、要么全失败的 SQL。ACID：

| 特性 | 含义 | InnoDB 实现 |
| --- | --- | --- |
| 原子性 Atomicity | 全做或全不做 | undo log 回滚 |
| 一致性 Consistency | 约束始终满足（如余票不为负） | 约束 + 应用逻辑 |
| 隔离性 Isolation | 并发事务互不干扰 | 锁 + MVCC |
| 持久性 Durability | 提交后不丢 | redo log（WAL） |

**为什么**：下单 = 扣库存 + 写订单，中途断电或异常必须回到原状，否则超卖或丢单。

**怎么排错**：`SELECT @@autocommit` 确认自动提交；忘记 `COMMIT` 会导致锁一直不释放；`SHOW ENGINE INNODB STATUS` 看事务状态。

**示例 SQL**：

```sql
START TRANSACTION;
UPDATE seat_inventory
SET remaining_count = remaining_count - 1, version = version + 1
WHERE train_id = 1 AND travel_date = '2026-04-15' AND seat_type = '二等座'
  AND from_station_order >= 1 AND to_station_order <= 4
  AND remaining_count >= 1;

INSERT INTO ticket_order (order_no, user_id, train_id, travel_date, from_station_id, to_station_id,
                          from_station_order, to_station_order, seat_type, passenger_name,
                          passenger_id_card, price, expire_at)
VALUES ('ORD20260415000099', 9001, 1, '2026-04-15', 1, 5, 1, 4, '二等座', '测试',
        '110101********0000', 716.50, '2026-04-15 10:15:00');
COMMIT;
```

---

## 2.8 隔离级别与可重复读下的幻读

**是什么**：SQL 标准四种隔离级别：

| 级别 | 脏读 | 不可重复读 | 幻读 |
| --- | --- | --- | --- |
| READ UNCOMMITTED | 可能 | 可能 | 可能 |
| READ COMMITTED | 不可能 | 可能 | 可能 |
| REPEATABLE READ（MySQL 默认） | 不可能 | 不可能 | 快照读不会，当前读可能 |
| SERIALIZABLE | 不可能 | 不可能 | 不可能 |

**为什么**：RR 下普通 `SELECT` 用的是事务开始时的快照（MVCC），天然看不到别的事务后来插入的行；但 `UPDATE/DELETE/SELECT ... FOR UPDATE` 是**当前读**，会看到最新数据，于是"同一事务前后读到的行集不一致"——这就是幻读。

**怎么排错/复现**（两个会话）：

```sql
-- 会话 A
START TRANSACTION;
SELECT COUNT(*) FROM candidate WHERE travel_date = '2026-04-17' AND status = 'WAITING';
-- 假设结果 0

-- 会话 B（另一个连接）
INSERT INTO candidate (candidate_no, user_id, train_id, travel_date, from_station_id, to_station_id,
                       from_station_order, to_station_order, seat_type)
VALUES ('CAND202604179999', 9999, 7, '2026-04-17', 8, 12, 1, 3, '二等座');
COMMIT;

-- 回到会话 A：当前读会看到 B 插入的行，更新成功
UPDATE candidate SET seat_type = '二等座'
WHERE travel_date = '2026-04-17' AND status = 'WAITING';
-- Rows matched: 1（A 一开始"看不到"的行被更新了 → 幻读）

-- 会话 A 再次快照读，仍然看不到（同一 ReadView）
SELECT COUNT(*) FROM candidate WHERE travel_date = '2026-04-17' AND status = 'WAITING';
ROLLBACK;
```

**示例 SQL**（用间隙锁防幻读，见下节）：

```sql
SELECT * FROM candidate
WHERE train_id = 7 AND travel_date = '2026-04-17' AND status = 'WAITING'
FOR UPDATE;  -- 锁住不存在的区间，B 的 INSERT 会被阻塞
```

---

## 2.9 MVCC：快照读的底层机制

**是什么**：MVCC（多版本并发控制）通过行的多个历史版本实现"读不加锁"。三个关键组件：

1. 隐藏字段：`DB_TRX_ID`（最近修改事务）、`DB_ROLL_PTR`（指向 undo log 旧版本）；
2. undo log：保存历史版本，形成版本链；
3. ReadView：事务快照读时生成，记录当时活跃事务集合，判断哪个版本对当前事务可见。

**为什么**：读操作不阻塞写、写操作不阻塞读，是 12306 这类读多写少系统的吞吐关键。RR 下 ReadView 在事务内复用，RC 下每条语句重新生成，所以 RC 更容易"不可重复读"。

**怎么排错**：长事务会导致 undo 链膨胀、快照过旧；`SHOW ENGINE INNODB STATUS` 的 History list length 过高要警惕；线上避免大事务。

**示例 SQL**：

```sql
SELECT @@transaction_isolation;                       -- 查看隔离级别
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED; -- 临时切换（课程演示）
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
```

---

## 2.10 行锁、间隙锁与 Next-Key Lock

**是什么**：

- **记录锁（Record Lock）**：锁住一条索引记录；
- **间隙锁（Gap Lock）**：锁住两条记录之间的"空隙"，防止插入；
- **Next-Key Lock**：记录锁 + 前面的间隙锁，InnoDB RR 下的默认加锁单位。

**为什么**：只锁记录挡不住"插入新记录"这种幻读，所以 RR 用间隙锁把区间也锁住。唯一索引等值命中时退化为记录锁（因为不存在要防的区间）；非唯一索引或范围条件会加 Next-Key Lock。

**怎么排错/观察**：

```sql
-- 会话 A：锁定一个不存在的区间（间隙锁）
START TRANSACTION;
SELECT * FROM candidate
WHERE train_id = 7 AND travel_date = '2026-04-17'
FOR UPDATE;

-- 查看锁
SELECT ENGINE_TRANSACTION_ID, OBJECT_NAME, INDEX_NAME, LOCK_TYPE, LOCK_MODE, LOCK_DATA
FROM performance_schema.data_locks;

-- 会话 B：这条 INSERT 会被阻塞，直到 A 提交/回滚
INSERT INTO candidate (candidate_no, user_id, train_id, travel_date, from_station_id, to_station_id,
                       from_station_order, to_station_order, seat_type)
VALUES ('CAND202604178888', 8888, 7, '2026-04-17', 8, 12, 1, 3, '二等座');
```

**踩坑提示**：

- 间隙锁会阻塞插入，高并发下容易引发锁等待甚至死锁；
- 若业务能接受 RC（比如用 binlog row 模式），间隙锁基本关闭，锁冲突更少；
- 本项目扣库存场景锁的是**明确的记录区间**（`FOR UPDATE` + 具体条件），要控制锁范围，避免锁全表。

**示例 SQL**（锁等待时间）：

```sql
SHOW VARIABLES LIKE 'innodb_lock_wait_timeout';  -- 默认 50 秒
```

---

## 2.11 死锁：发生、排查与预防

**是什么**：两个事务互相持有对方需要的锁，形成环路。InnoDB 会自动检测，回滚代价小的一方，报错 `Deadlock found when trying to get lock`。

**为什么**：加锁顺序不一致（A 先锁 1 后锁 2，B 先锁 2 后锁 1）、大事务、间隙锁都是死锁温床。

**怎么排错**：

```sql
SHOW ENGINE INNODB STATUS;  -- 查看 LATEST DETECTED DEADLOCK 段落

-- 8.0 更推荐：
SELECT * FROM performance_schema.data_lock_waits;
SET GLOBAL innodb_print_all_deadlocks = ON;  -- 把所有死锁打进 error log
```

**预防**：

1. 统一加锁顺序（比如按 train_id、station_order 升序扣减区段）；
2. 事务尽量短小，不在事务里做远程调用；
3. 用唯一索引/主键点查，减少间隙锁；
4. 业务层对死锁做捕获与重试（重试前随机退避）；
5. 必要时降低隔离级别到 RC。

**示例 SQL**（死锁复现骨架）：

```sql
-- 会话 A                          -- 会话 B
BEGIN;                             BEGIN;
UPDATE seat_inventory SET remaining_count = remaining_count - 1 WHERE id = 1;
                                   UPDATE seat_inventory SET remaining_count = remaining_count - 1 WHERE id = 2;
UPDATE seat_inventory SET remaining_count = remaining_count - 1 WHERE id = 2;
                                   UPDATE seat_inventory SET remaining_count = remaining_count - 1 WHERE id = 1;
-- 互相等待 → 其中一方被回滚
```

本项目统一按 `from_station_order` 升序批量更新，即是为了避免这种环路。

---

## 2.12 慢查询：定位与优化闭环

**是什么**：执行时间超过 `long_query_time` 的 SQL 会被记入慢查询日志。

**为什么**：EXPLAIN 是单条 SQL 的解剖刀，慢日志是全局的雷达，两者配合形成优化闭环。

**怎么排错**（开启与查看）：

```sql
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 0.5;
SET GLOBAL log_queries_not_using_indexes = ON;
SHOW VARIABLES LIKE 'slow_query_log_file';
```

```bash
mysqldumpslow -s t /var/lib/mysql/slow.log | head -20
# 或用 pt-query-digest 做统计报告
```

**优化套路**：

1. 先看 `rows` 和 `filtered`，确认扫了多少行；
2. 给 WHERE / JOIN / ORDER BY / GROUP BY 涉及的列建合适索引（联合索引注意列序）；
3. 避免 `SELECT *`，尽量覆盖索引；
4. 大分页改游标（`WHERE id > ? LIMIT n`）；
5. 确认统计信息新鲜：`ANALYZE TABLE`。

**示例 SQL**（本项目候补聚合的优化闭环）：

```sql
-- 优化前：type=ALL, rows=3001, Using temporary; Using filesort
-- 优化后：type=ref, key=idx_agg, rows=1018, Using index condition
-- 复现实验：init.sql 建表时已创建 idx_agg，先删除再重建才能看到前后对比
DROP INDEX idx_agg ON candidate;
ALTER TABLE candidate ADD INDEX idx_agg (travel_date, from_station_id, to_station_id, status);
ANALYZE TABLE candidate;
```

---

## 2.13 本项目实战：三条核心 SQL

### 2.13.1 余票查询（席位复用的读）

```sql
SELECT MIN(remaining_count) AS remaining
FROM seat_inventory
WHERE train_id = 1 AND travel_date = '2026-04-15' AND seat_type = '二等座'
  AND from_station_order >= 1 AND to_station_order <= 4;
-- 实测返回 3：瓶颈区段是济南西→南京南
```

### 2.13.2 候补聚合（加开建议的输入）

```sql
SELECT c.travel_date,
       fs.station_name AS from_station,
       ts.station_name AS to_station,
       COUNT(*)        AS candidate_count
FROM candidate c
         JOIN station fs ON fs.id = c.from_station_id
         JOIN station ts ON ts.id = c.to_station_id
WHERE c.status = 'WAITING' AND c.travel_date BETWEEN '2026-04-15' AND '2026-04-16'
GROUP BY c.travel_date, fs.station_name, ts.station_name, c.seat_type
HAVING COUNT(*) >= 3
ORDER BY candidate_count DESC;
```

### 2.13.3 扣减库存（防超卖的写）

```sql
START TRANSACTION;
SELECT remaining_count FROM seat_inventory
WHERE train_id = 1 AND travel_date = '2026-04-15' AND seat_type = '二等座'
  AND from_station_order >= 1 AND to_station_order <= 4
FOR UPDATE;

UPDATE seat_inventory
SET remaining_count = remaining_count - 1, version = version + 1
WHERE train_id = 1 AND travel_date = '2026-04-15' AND seat_type = '二等座'
  AND from_station_order >= 1 AND to_station_order <= 4
  AND remaining_count >= 1;
COMMIT;
```

---

## 2.14 自检三问（对齐验收标准）

1. **EXPLAIN 看出全表扫描**：`type=ALL`、`key=NULL`、`rows` 接近表总行数 → 给 WHERE/GROUP BY 建联合索引 → 再次 EXPLAIN 看到 `type=ref/range`、`key=idx_agg`。
2. **可重复读下幻读**：快照读用 MVCC 不会幻读；当前读（`UPDATE`/`FOR UPDATE`）会读到新插入的行，表现为幻读；用 Next-Key Lock 锁区间可阻止插入。
3. **席位复用**：`seat_inventory` 每行是一个相邻区段（`from_station_order`、`to_station_order`）；查余票取区间内 `MIN`，扣库存对区间内所有区段批量扣减，因此一个座位能在不重叠区段被多段售卖。

面试追问预演：

- "为什么不用一个库存字段？"→ 无法支持区段复用，会浪费运力。
- "为什么索引有时候不生效？"→ 成本模型 + 最左前缀 + 表达式破坏有序性，见 2.6/3.8。
- "怎么防超卖？"→ 第2章先有 `remaining_count >= 1` 与事务；第4章再上 Redis 分布式锁与 MQ 削峰。

---

## 2.15 下一章预告

**第3章 MyBatis 与 Spring Boot 整合**：把本章的 SQL 变成 `GET /api/trains?from=北京&to=上海&date=2026-04-15`，用 Mapper XML 写动态 SQL、ResultMap 映射实体，并用 Spring 事务保证"下单扣库存"的一致性。
