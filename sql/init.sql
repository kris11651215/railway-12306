-- =====================================================================
-- 铁路智慧出行综合服务平台 数据库初始化脚本（第2章）
-- 数据库：railway  字符集：utf8mb4  引擎：InnoDB
-- 说明：先建表，再灌样例数据，最后是查询/优化示例（真实可执行）
-- =====================================================================

DROP DATABASE IF EXISTS railway;
CREATE DATABASE railway DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE railway;

SET NAMES utf8mb4;
SET SESSION cte_max_recursion_depth = 5000;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS station_rule;
DROP TABLE IF EXISTS candidate;
DROP TABLE IF EXISTS ticket_order;
DROP TABLE IF EXISTS seat_inventory;
DROP TABLE IF EXISTS train_station;
DROP TABLE IF EXISTS train;
DROP TABLE IF EXISTS station;
SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 1. station 车站表
-- =====================================================================
CREATE TABLE station (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '车站主键',
    station_code     VARCHAR(10)     NOT NULL                COMMENT '电报码，如 VNP',
    station_name     VARCHAR(50)     NOT NULL                COMMENT '站名，如 北京南',
    city             VARCHAR(50)     NOT NULL                COMMENT '所在城市',
    bureau           VARCHAR(50)     NOT NULL                COMMENT '所属路局，如 广铁集团',
    affiliated_depot VARCHAR(50)     DEFAULT NULL            COMMENT '所属机务段/车辆段',
    station_class    VARCHAR(10)     NOT NULL                COMMENT '站等级：特等站/一等站...',
    is_hub           TINYINT         NOT NULL DEFAULT 0      COMMENT '是否枢纽站：1是 0否',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_station_code (station_code),
    UNIQUE KEY uk_station_name (station_name),
    KEY idx_bureau (bureau)
) ENGINE = InnoDB COMMENT = '车站表';

-- =====================================================================
-- 2. train 车次表
-- =====================================================================
CREATE TABLE train (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '车次主键',
    train_no         VARCHAR(10)     NOT NULL                COMMENT '车次号，如 G1',
    train_type       CHAR(1)         NOT NULL                COMMENT '等级：G/D/C/Z/T/K/P',
    direction        ENUM('UP','DOWN') NOT NULL              COMMENT 'UP上行(北京方向) DOWN下行',
    start_station_id BIGINT UNSIGNED NOT NULL                COMMENT '始发站ID',
    end_station_id   BIGINT UNSIGNED NOT NULL                COMMENT '终到站ID',
    bureau           VARCHAR(50)     NOT NULL                COMMENT '担当路局',
    departure_time   TIME            NOT NULL                COMMENT '始发时刻',
    arrival_time     TIME            NOT NULL                COMMENT '终到时刻',
    duration_minutes INT             NOT NULL                COMMENT '全程历时(分钟)',
    mileage          INT             NOT NULL                COMMENT '全程里程(km)',
    status           TINYINT         NOT NULL DEFAULT 1      COMMENT '1开行 0停运',
    PRIMARY KEY (id),
    UNIQUE KEY uk_train_no (train_no),
    KEY idx_route (start_station_id, end_station_id),
    KEY idx_train_type (train_type),
    CONSTRAINT fk_train_start FOREIGN KEY (start_station_id) REFERENCES station (id),
    CONSTRAINT fk_train_end FOREIGN KEY (end_station_id) REFERENCES station (id)
) ENGINE = InnoDB COMMENT = '车次表';

-- =====================================================================
-- 3. train_station 经停表
-- =====================================================================
CREATE TABLE train_station (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    train_id          BIGINT UNSIGNED NOT NULL                COMMENT '车次ID',
    station_id        BIGINT UNSIGNED NOT NULL                COMMENT '车站ID',
    station_order     INT             NOT NULL                COMMENT '站序，从1开始',
    arrival_time      TIME            DEFAULT NULL            COMMENT '到达时刻，始发站为NULL',
    departure_time    TIME            DEFAULT NULL            COMMENT '发车时刻，终到站为NULL',
    stop_minutes      INT             NOT NULL DEFAULT 0      COMMENT '停站分钟',
    mileage_from_start INT            NOT NULL DEFAULT 0      COMMENT '距始发站里程(km)',
    day_offset        TINYINT         NOT NULL DEFAULT 0      COMMENT '相对始发日的天偏移，跨天车次用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_train_order (train_id, station_order),
    KEY idx_station (station_id),
    CONSTRAINT fk_ts_train FOREIGN KEY (train_id) REFERENCES train (id),
    CONSTRAINT fk_ts_station FOREIGN KEY (station_id) REFERENCES station (id)
) ENGINE = InnoDB COMMENT = '车次经停表：站序是席位复用/换乘计算的基石';

-- =====================================================================
-- 4. seat_inventory 座位库存表（席位复用的核心）
--    每行 = 一个原子区段（约定为相邻两站之间，样例数据均为相邻段；
--    CHECK 仅保证 to_station_order > from_station_order，相邻性由应用层保证）
--    查询 O-D 余票 = 区间内所有原子区段 remaining_count 的最小值
--    购票扣减 = 区间内所有原子区段 remaining_count 同时减 N
-- =====================================================================
CREATE TABLE seat_inventory (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    train_id          BIGINT UNSIGNED NOT NULL                COMMENT '车次ID',
    travel_date       DATE            NOT NULL                COMMENT '乘车日期(始发日)',
    seat_type         VARCHAR(20)     NOT NULL                COMMENT '席别：商务座/一等座/二等座/硬座/硬卧/软卧',
    from_station_order INT            NOT NULL                COMMENT '区段起始站序',
    to_station_order   INT            NOT NULL                COMMENT '区段终止站序',
    total_count       INT             NOT NULL                COMMENT '区段定员',
    remaining_count   INT             NOT NULL                COMMENT '区段余票',
    price             DECIMAL(10, 2)  NOT NULL                COMMENT '区段票价',
    version           INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_segment (train_id, travel_date, seat_type, from_station_order, to_station_order),
    CONSTRAINT fk_inv_train FOREIGN KEY (train_id) REFERENCES train (id),
    CONSTRAINT chk_segment CHECK (to_station_order > from_station_order),
    CONSTRAINT chk_remaining CHECK (remaining_count >= 0)
) ENGINE = InnoDB COMMENT = '座位库存表：按原子区段存储，支持席位复用';

-- =====================================================================
-- 5. ticket_order 订单表（规划中的 order，order 是 MySQL 保留字故加前缀）
-- =====================================================================
CREATE TABLE ticket_order (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单主键',
    order_no           VARCHAR(32)     NOT NULL                COMMENT '订单号',
    user_id            BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
    train_id           BIGINT UNSIGNED NOT NULL                COMMENT '车次ID',
    travel_date        DATE            NOT NULL                COMMENT '乘车日期',
    from_station_id    BIGINT UNSIGNED NOT NULL                COMMENT '出发站ID',
    to_station_id      BIGINT UNSIGNED NOT NULL                COMMENT '到达站ID',
    from_station_order INT             NOT NULL                COMMENT '出发站序',
    to_station_order   INT             NOT NULL                COMMENT '到达站序',
    seat_type          VARCHAR(20)     NOT NULL                COMMENT '席别',
    carriage_no        VARCHAR(10)     DEFAULT NULL            COMMENT '车厢号',
    seat_no            VARCHAR(10)     DEFAULT NULL            COMMENT '座位号',
    passenger_name     VARCHAR(50)     NOT NULL                COMMENT '乘车人姓名',
    passenger_id_card  VARCHAR(64)     NOT NULL                COMMENT '证件号（加密存储，展示脱敏）',
    price              DECIMAL(10, 2)  NOT NULL                COMMENT '实付金额',
    status             ENUM('PENDING_PAYMENT','PAID','CANCELLED','REFUNDED')
                       NOT NULL DEFAULT 'PENDING_PAYMENT'      COMMENT '订单状态',
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    paid_at            DATETIME        DEFAULT NULL            COMMENT '支付时间',
    expire_at          DATETIME        NOT NULL                COMMENT '支付截止时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_created (user_id, created_at),
    KEY idx_train_date (train_id, travel_date),
    KEY idx_status_expire (status, expire_at),
    CONSTRAINT fk_order_train FOREIGN KEY (train_id) REFERENCES train (id),
    CONSTRAINT fk_order_from FOREIGN KEY (from_station_id) REFERENCES station (id),
    CONSTRAINT fk_order_to FOREIGN KEY (to_station_id) REFERENCES station (id)
) ENGINE = InnoDB COMMENT = '订单表：预扣库存+超时释放的载体';

-- =====================================================================
-- 6. candidate 候补表
-- =====================================================================
CREATE TABLE candidate (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '候补主键',
    candidate_no       VARCHAR(32)     NOT NULL                COMMENT '候补单号',
    user_id            BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
    train_id           BIGINT UNSIGNED NOT NULL                COMMENT '车次ID',
    travel_date        DATE            NOT NULL                COMMENT '乘车日期',
    from_station_id    BIGINT UNSIGNED NOT NULL                COMMENT '出发站ID',
    to_station_id      BIGINT UNSIGNED NOT NULL                COMMENT '到达站ID',
    from_station_order INT             NOT NULL                COMMENT '出发站序',
    to_station_order   INT             NOT NULL                COMMENT '到达站序',
    seat_type          VARCHAR(20)     NOT NULL                COMMENT '席别',
    status             ENUM('WAITING','FULFILLED','CANCELLED','EXPIRED')
                       NOT NULL DEFAULT 'WAITING'              COMMENT '候补状态',
    candidate_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '候补下单时间',
    expire_time        DATETIME        DEFAULT NULL            COMMENT '候补截止时间',
    fulfilled_time     DATETIME        DEFAULT NULL            COMMENT '兑现时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_candidate_no (candidate_no),
    KEY idx_agg (travel_date, from_station_id, to_station_id, status),
    KEY idx_train_date (train_id, travel_date),
    KEY idx_user (user_id, candidate_time),
    CONSTRAINT fk_cand_train FOREIGN KEY (train_id) REFERENCES train (id),
    CONSTRAINT fk_cand_from FOREIGN KEY (from_station_id) REFERENCES station (id),
    CONSTRAINT fk_cand_to FOREIGN KEY (to_station_id) REFERENCES station (id)
) ENGINE = InnoDB COMMENT = '候补表：按日期+OD聚合分析，驱动加开建议';

-- =====================================================================
-- 7. station_rule 站内规则表
-- =====================================================================
CREATE TABLE station_rule (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '规则主键',
    station_id        BIGINT UNSIGNED NOT NULL                COMMENT '车站ID',
    train_no          VARCHAR(10)     NOT NULL                COMMENT '车次号',
    formation_length  INT             NOT NULL                COMMENT '编组长度(辆)',
    track_no          VARCHAR(10)     NOT NULL                COMMENT '停靠股道',
    entry_direction   VARCHAR(20)     NOT NULL                COMMENT '进站方向',
    passenger_floor   VARCHAR(10)     NOT NULL                COMMENT '旅客所在楼层',
    recommended_gate  VARCHAR(20)     NOT NULL                COMMENT '推荐检票口',
    walking_route     VARCHAR(255)    NOT NULL                COMMENT '推荐走行路线',
    walking_minutes   INT             NOT NULL                COMMENT '预估步行分钟',
    priority          INT             NOT NULL DEFAULT 0      COMMENT '规则优先级，越大越优先',
    PRIMARY KEY (id),
    KEY idx_station_train (station_id, train_no),
    CONSTRAINT fk_rule_station FOREIGN KEY (station_id) REFERENCES station (id)
) ENGINE = InnoDB COMMENT = '站内规则表：广州南站检票口/走行路线推荐';

-- =====================================================================
-- 样例数据：车站
-- =====================================================================
INSERT INTO station (id, station_code, station_name, city, bureau, affiliated_depot, station_class, is_hub) VALUES
(1,  'VNP', '北京南',   '北京', '北京局集团', '北京动车段',     '特等站', 1),
(2,  'BXP', '北京西',   '北京', '北京局集团', '北京车辆段',     '特等站', 1),
(3,  'JGK', '济南西',   '济南', '济南局集团', '济南动车运用所', '一等站', 0),
(4,  'NKH', '南京南',   '南京', '上海局集团', '南京动车段',     '特等站', 1),
(5,  'AOH', '上海虹桥', '上海', '上海局集团', '上海动车段',     '特等站', 1),
(6,  'SHH', '上海',     '上海', '上海局集团', '上海车辆段',     '特等站', 1),
(7,  'HGH', '杭州东',   '杭州', '上海局集团', '杭州动车运用所', '一等站', 0),
(8,  'IZQ', '广州南',   '广州', '广铁集团',   '广州动车段',     '特等站', 1),
(9,  'IOQ', '深圳北',   '深圳', '广铁集团',   '深圳动车运用所', '一等站', 0),
(10, 'IUQ', '虎门',     '东莞', '广铁集团',   '广州动车段',     '三等站', 0),
(11, 'CWQ', '长沙南',   '长沙', '广铁集团',   '长沙动车运用所', '特等站', 1),
(12, 'WHN', '武汉',     '武汉', '武汉局集团', '武汉动车段',     '特等站', 1),
(13, 'ZAF', '郑州东',   '郑州', '郑州局集团', '郑州动车段',     '特等站', 1);

-- =====================================================================
-- 样例数据：车次
-- =====================================================================
INSERT INTO train (id, train_no, train_type, direction, start_station_id, end_station_id, bureau,
                   departure_time, arrival_time, duration_minutes, mileage, status) VALUES
(1, 'G1',    'G', 'DOWN', 1,  5,  '上海局集团', '09:00', '13:28',  268, 1318, 1),
(2, 'G2',    'G', 'UP',   5,  1,  '上海局集团', '07:00', '11:29',  269, 1318, 1),
(3, 'G11',   'G', 'DOWN', 1,  7,  '北京局集团', '08:00', '12:48',  288, 1480, 1),
(4, 'G100',  'G', 'UP',   5,  1,  '上海局集团', '14:00', '18:28',  268, 1318, 1),
(5, 'G6001', 'G', 'DOWN', 8,  9,  '广铁集团',   '08:30', '09:02',   32,  102, 1),
(6, 'G6002', 'G', 'UP',   9,  8,  '广铁集团',   '09:30', '10:05',   35,  102, 1),
(7, 'G1102', 'G', 'UP',   8,  12, '广铁集团',   '10:00', '13:48',  228, 1069, 1),
(8, 'G1101', 'G', 'DOWN', 12, 8,  '广铁集团',   '14:00', '17:55',  235, 1069, 1),
(9, 'K599',  'K', 'DOWN', 2,  8,  '广铁集团',   '12:30', '05:10', 1000, 2298, 1);

-- =====================================================================
-- 样例数据：经停（跨天车次用 day_offset 标记）
-- =====================================================================
INSERT INTO train_station (train_id, station_id, station_order, arrival_time, departure_time,
                           stop_minutes, mileage_from_start, day_offset) VALUES
(1, 1,  1, NULL,    '09:00', 0,    0, 0),
(1, 3,  2, '10:22', '10:24', 2,  406, 0),
(1, 4,  3, '12:04', '12:06', 2, 1023, 0),
(1, 5,  4, '13:28', NULL,    0, 1318, 0),
(2, 5,  1, NULL,    '07:00', 0,    0, 0),
(2, 4,  2, '08:23', '08:25', 2,  295, 0),
(2, 3,  3, '10:05', '10:07', 2,  912, 0),
(2, 1,  4, '11:29', NULL,    0, 1318, 0),
(3, 1,  1, NULL,    '08:00', 0,    0, 0),
(3, 3,  2, '09:22', '09:24', 2,  406, 0),
(3, 4,  3, '11:04', '11:06', 2, 1023, 0),
(3, 7,  4, '12:48', NULL,    0, 1480, 0),
(4, 5,  1, NULL,    '14:00', 0,    0, 0),
(4, 4,  2, '15:23', '15:25', 2,  295, 0),
(4, 3,  3, '17:05', '17:07', 2,  912, 0),
(4, 1,  4, '18:28', NULL,    0, 1318, 0),
(5, 8,  1, NULL,    '08:30', 0,    0, 0),
(5, 10, 2, '08:47', '08:48', 1,   50, 0),
(5, 9,  3, '09:02', NULL,    0,  102, 0),
(6, 9,  1, NULL,    '09:30', 0,    0, 0),
(6, 10, 2, '09:43', '09:44', 1,   50, 0),
(6, 8,  3, '10:05', NULL,    0,  102, 0),
(7, 8,  1, NULL,    '10:00', 0,    0, 0),
(7, 11, 2, '12:03', '12:06', 3,  707, 0),
(7, 12, 3, '13:48', NULL,    0, 1069, 0),
(8, 12, 1, NULL,    '14:00', 0,    0, 0),
(8, 11, 2, '15:41', '15:44', 3,  362, 0),
(8, 8,  3, '17:55', NULL,    0, 1069, 0),
(9, 2,  1, NULL,    '12:30', 0,    0, 0),
(9, 13, 2, '17:52', '18:00', 8,  693, 0),
(9, 12, 3, '21:30', '21:38', 8, 1229, 0),
(9, 11, 4, '00:50', '00:58', 8, 1591, 1),
(9, 8,  5, '05:10', NULL,    0, 2298, 1);

-- =====================================================================
-- 样例数据：座位库存（原子区段）
-- 区间票价按递远递减示意，非真实票价
-- =====================================================================
INSERT INTO seat_inventory (train_id, travel_date, seat_type, from_station_order, to_station_order,
                            total_count, remaining_count, price, version) VALUES
(1, '2026-04-15', '商务座', 1, 2, 10,  8,  384.00, 0),
(1, '2026-04-15', '商务座', 2, 3, 10,  6,  586.00, 0),
(1, '2026-04-15', '商务座', 3, 4, 10,  9,  276.00, 0),
(1, '2026-04-15', '一等座', 1, 2, 28, 20,  372.00, 0),
(1, '2026-04-15', '一等座', 2, 3, 28, 15,  566.00, 0),
(1, '2026-04-15', '一等座', 3, 4, 28, 22,  267.00, 0),
(1, '2026-04-15', '二等座', 1, 2, 500, 320, 232.50, 0),
(1, '2026-04-15', '二等座', 2, 3, 500,   3, 344.50, 0),
(1, '2026-04-15', '二等座', 3, 4, 500, 210, 139.50, 0),
(1, '2026-04-16', '二等座', 1, 2, 500, 480, 232.50, 0),
(1, '2026-04-16', '二等座', 2, 3, 500, 450, 344.50, 0),
(1, '2026-04-16', '二等座', 3, 4, 500, 460, 139.50, 0),
(3, '2026-04-15', '商务座', 1, 2, 10,  6,  384.00, 0),
(3, '2026-04-15', '商务座', 2, 3, 10,  5,  586.00, 0),
(3, '2026-04-15', '商务座', 3, 4, 10,  6,  437.00, 0),
(3, '2026-04-15', '一等座', 1, 2, 28, 30,  372.00, 0),
(3, '2026-04-15', '一等座', 2, 3, 28, 25,  566.00, 0),
(3, '2026-04-15', '一等座', 3, 4, 28, 28,  423.00, 0),
(3, '2026-04-15', '二等座', 1, 2, 500, 200, 232.50, 0),
(3, '2026-04-15', '二等座', 2, 3, 500, 150, 344.50, 0),
(3, '2026-04-15', '二等座', 3, 4, 500, 180, 220.50, 0),
(5, '2026-04-15', '商务座', 1, 2, 4,   4,  45.00, 0),
(5, '2026-04-15', '商务座', 2, 3, 4,   3,  55.00, 0),
(5, '2026-04-15', '一等座', 1, 2, 20, 18,  36.00, 0),
(5, '2026-04-15', '一等座', 2, 3, 20, 16,  44.00, 0),
(5, '2026-04-15', '二等座', 1, 2, 100, 80, 25.00, 0),
(5, '2026-04-15', '二等座', 2, 3, 100, 75, 34.50, 0),
(7, '2026-04-15', '商务座', 1, 2, 8,   8,  406.00, 0),
(7, '2026-04-15', '商务座', 2, 3, 8,   7,  302.00, 0),
(7, '2026-04-15', '一等座', 1, 2, 40, 36,  356.00, 0),
(7, '2026-04-15', '一等座', 2, 3, 40, 33,  265.00, 0),
(7, '2026-04-15', '二等座', 1, 2, 300, 260, 220.50, 0),
(7, '2026-04-15', '二等座', 2, 3, 300, 240, 164.00, 0),
(7, '2026-04-16', '二等座', 1, 2, 300, 280, 220.50, 0),
(7, '2026-04-16', '二等座', 2, 3, 300, 270, 164.00, 0),
(9, '2026-04-15', '硬座', 1, 2, 800, 600, 105.00, 0),
(9, '2026-04-15', '硬座', 2, 3, 800, 520,  88.00, 0),
(9, '2026-04-15', '硬座', 3, 4, 800, 480,  76.00, 0),
(9, '2026-04-15', '硬座', 4, 5, 800, 430, 115.00, 0),
(9, '2026-04-15', '硬卧', 1, 2, 400, 300, 198.00, 0),
(9, '2026-04-15', '硬卧', 2, 3, 400, 260, 166.00, 0),
(9, '2026-04-15', '硬卧', 3, 4, 400, 240, 143.00, 0),
(9, '2026-04-15', '硬卧', 4, 5, 400, 210, 217.00, 0),
(9, '2026-04-15', '软卧', 1, 2, 80,  50, 298.00, 0),
(9, '2026-04-15', '软卧', 2, 3, 80,  44, 258.00, 0),
(9, '2026-04-15', '软卧', 3, 4, 80,  40, 224.00, 0),
(9, '2026-04-15', '软卧', 4, 5, 80,  36, 336.00, 0);

-- 批量生成区间库存：2026-04-17 ~ 2026-05-16，覆盖 G1/G11/G6001/G1101
-- 每个相邻区段、每个席别一行，用于验证索引效果
INSERT INTO seat_inventory (train_id, travel_date, seat_type, from_station_order, to_station_order,
                            total_count, remaining_count, price, version)
WITH RECURSIVE days AS (SELECT 0 AS d
                        UNION ALL
                        SELECT d + 1 FROM days WHERE d < 29),
               seat_types AS (SELECT '商务座' AS seat_type, 10 AS total_count
                              UNION ALL
                              SELECT '一等座', 28
                              UNION ALL
                              SELECT '二等座', 500)
SELECT ts1.train_id,
       DATE_ADD('2026-04-17', INTERVAL d DAY),
       st.seat_type,
       ts1.station_order,
       ts2.station_order,
       st.total_count,
       FLOOR(st.total_count * (0.3 + ((ts1.station_order * 7 + d) % 7) * 0.1)),
       ROUND(50 + (ts1.station_order * 37 + d) % 200 + (ts2.station_order - ts1.station_order) * 30, 2),
       0
FROM days
         CROSS JOIN seat_types st
         JOIN train_station ts1 ON ts1.train_id IN (1, 3, 5, 7)
         JOIN train_station ts2 ON ts2.train_id = ts1.train_id AND ts2.station_order = ts1.station_order + 1;

-- =====================================================================
-- 样例数据：订单
-- =====================================================================
INSERT INTO ticket_order (order_no, user_id, train_id, travel_date, from_station_id, to_station_id,
                          from_station_order, to_station_order, seat_type, carriage_no, seat_no,
                          passenger_name, passenger_id_card, price, status, created_at, paid_at, expire_at) VALUES
('ORD20260415000001', 1001, 1, '2026-04-15', 1, 4, 1, 3, '二等座', '08', '12A', '张三', '110101********1234', 577.00, 'PAID',            '2026-04-01 09:00:00', '2026-04-01 09:03:00', '2026-04-01 09:15:00'),
('ORD20260415000002', 1002, 1, '2026-04-15', 1, 4, 1, 3, '二等座', '08', '12B', '李四', '110101********5678', 577.00, 'PAID',            '2026-04-01 09:05:00', '2026-04-01 09:07:00', '2026-04-01 09:20:00'),
('ORD20260415000003', 1003, 7, '2026-04-15', 8, 12, 1, 3, '二等座', '05', '03C', '王五', '440101********9012', 384.50, 'PENDING_PAYMENT', '2026-04-14 10:00:00', NULL,                  '2026-04-14 10:15:00'),
('ORD20260416000004', 1004, 5, '2026-04-15', 8, 9, 1, 3, '二等座', '03', '05F', '赵六', '440101********3456', 59.50,  'PAID',            '2026-04-14 11:00:00', '2026-04-14 11:01:00', '2026-04-14 11:15:00'),
('ORD20260416000005', 1005, 9, '2026-04-15', 2, 8, 1, 5, '硬卧',   NULL, NULL,  '孙七', '110101********7890', 724.00,  'REFUNDED',        '2026-03-30 08:00:00', '2026-03-30 08:02:00', '2026-03-30 08:15:00');

-- =====================================================================
-- 样例数据：候补（用于聚合分析）
-- =====================================================================
INSERT INTO candidate (candidate_no, user_id, train_id, travel_date, from_station_id, to_station_id,
                       from_station_order, to_station_order, seat_type, status, candidate_time, expire_time, fulfilled_time) VALUES
('CAND202604150001', 2001, 7, '2026-04-15', 8, 12, 1, 3, '二等座', 'WAITING', '2026-04-10 08:00:00', '2026-04-15 09:00:00', NULL),
('CAND202604150002', 2002, 7, '2026-04-15', 8, 12, 1, 3, '二等座', 'WAITING', '2026-04-10 08:05:00', '2026-04-15 09:00:00', NULL),
('CAND202604150003', 2003, 7, '2026-04-15', 8, 12, 1, 3, '二等座', 'WAITING', '2026-04-10 08:10:00', '2026-04-15 09:00:00', NULL),
('CAND202604150004', 2004, 7, '2026-04-15', 8, 12, 1, 3, '二等座', 'WAITING', '2026-04-10 09:00:00', '2026-04-15 09:00:00', NULL),
('CAND202604150005', 2005, 7, '2026-04-15', 8, 12, 1, 3, '二等座', 'WAITING', '2026-04-10 09:30:00', '2026-04-15 09:00:00', NULL),
('CAND202604150006', 2006, 7, '2026-04-15', 8, 12, 1, 3, '二等座', 'WAITING', '2026-04-11 10:00:00', '2026-04-15 09:00:00', NULL),
('CAND202604150007', 2007, 7, '2026-04-15', 8, 12, 1, 3, '二等座', 'WAITING', '2026-04-11 10:20:00', '2026-04-15 09:00:00', NULL),
('CAND202604150008', 2008, 7, '2026-04-15', 8, 12, 1, 3, '一等座', 'WAITING', '2026-04-11 11:00:00', '2026-04-15 09:00:00', NULL),
('CAND202604150009', 2009, 5, '2026-04-15', 8, 9, 1, 3, '二等座', 'WAITING', '2026-04-12 08:00:00', '2026-04-15 08:00:00', NULL),
('CAND202604150010', 2010, 5, '2026-04-15', 8, 9, 1, 3, '二等座', 'WAITING', '2026-04-12 08:30:00', '2026-04-15 08:00:00', NULL),
('CAND202604150011', 2011, 5, '2026-04-15', 8, 9, 1, 3, '二等座', 'WAITING', '2026-04-12 09:00:00', '2026-04-15 08:00:00', NULL),
('CAND202604150012', 2012, 1, '2026-04-15', 1, 5, 1, 4, '二等座', 'WAITING', '2026-04-13 14:00:00', '2026-04-15 08:00:00', NULL),
('CAND202604150013', 2013, 1, '2026-04-15', 1, 5, 1, 4, '二等座', 'WAITING', '2026-04-13 14:10:00', '2026-04-15 08:00:00', NULL),
('CAND202604150014', 2014, 1, '2026-04-15', 1, 5, 1, 4, '二等座', 'WAITING', '2026-04-13 14:20:00', '2026-04-15 08:00:00', NULL),
('CAND202604150015', 2015, 1, '2026-04-15', 1, 5, 1, 4, '二等座', 'WAITING', '2026-04-13 15:00:00', '2026-04-15 08:00:00', NULL),
('CAND202604160001', 2016, 7, '2026-04-16', 8, 12, 1, 3, '二等座', 'WAITING', '2026-04-12 08:00:00', '2026-04-16 09:00:00', NULL),
('CAND202604160002', 2017, 7, '2026-04-16', 8, 12, 1, 3, '二等座', 'WAITING', '2026-04-12 08:30:00', '2026-04-16 09:00:00', NULL),
('CAND202604160003', 2018, 7, '2026-04-16', 8, 12, 1, 3, '二等座', 'WAITING', '2026-04-12 09:00:00', '2026-04-16 09:00:00', NULL),
('CAND202604160004', 2019, 7, '2026-04-16', 8, 12, 1, 3, '二等座', 'WAITING', '2026-04-12 10:00:00', '2026-04-16 09:00:00', NULL),
('CAND202604160005', 2020, 7, '2026-04-16', 8, 12, 1, 3, '二等座', 'WAITING', '2026-04-12 11:00:00', '2026-04-16 09:00:00', NULL),
('CAND202604160006', 2021, 3, '2026-04-16', 1, 7, 1, 4, '二等座', 'WAITING', '2026-04-13 08:00:00', '2026-04-16 07:00:00', NULL),
('CAND202604160007', 2022, 3, '2026-04-16', 1, 7, 1, 4, '二等座', 'WAITING', '2026-04-13 09:00:00', '2026-04-16 07:00:00', NULL),
('CAND202604140001', 2023, 7, '2026-04-15', 8, 12, 1, 3, '二等座', 'FULFILLED', '2026-04-08 08:00:00', '2026-04-15 09:00:00', '2026-04-09 10:00:00'),
('CAND202604140002', 2024, 7, '2026-04-15', 8, 12, 1, 3, '二等座', 'EXPIRED',   '2026-04-08 09:00:00', '2026-04-15 09:00:00', NULL),
('CAND202604140003', 2025, 5, '2026-04-15', 8, 9, 1, 3, '二等座', 'CANCELLED', '2026-04-09 09:00:00', '2026-04-15 08:00:00', NULL);

-- 批量生成候补数据：3000 条，覆盖多个 OD/日期/状态，用于聚合与 EXPLAIN 演示
INSERT INTO candidate (candidate_no, user_id, train_id, travel_date, from_station_id, to_station_id,
                       from_station_order, to_station_order, seat_type, status, candidate_time, expire_time)
WITH RECURSIVE seq AS (SELECT 0 AS n
                       UNION ALL
                       SELECT n + 1 FROM seq WHERE n < 2999)
SELECT CONCAT('CANDGEN', LPAD(n + 1, 8, '0')),
       10000 + n,
       CASE n % 4 WHEN 0 THEN 7 WHEN 1 THEN 5 WHEN 2 THEN 1 ELSE 3 END,
       DATE_ADD('2026-04-15', INTERVAL n % 3 DAY),
       CASE n % 4 WHEN 0 THEN 8 WHEN 1 THEN 8 WHEN 2 THEN 1 ELSE 1 END,
       CASE n % 4 WHEN 0 THEN 12 WHEN 1 THEN 9 WHEN 2 THEN 5 ELSE 7 END,
       1,
       CASE n % 4 WHEN 0 THEN 3 WHEN 1 THEN 3 WHEN 2 THEN 4 ELSE 4 END,
       CASE n % 5 WHEN 0 THEN '一等座' ELSE '二等座' END,
       CASE n % 10 WHEN 0 THEN 'FULFILLED' WHEN 1 THEN 'CANCELLED' WHEN 2 THEN 'EXPIRED' ELSE 'WAITING' END,
       DATE_ADD('2026-04-01 08:00:00', INTERVAL n MINUTE),
       '2026-04-20 09:00:00'
FROM seq;

-- =====================================================================
-- 样例数据：站内规则（广州南站）
-- =====================================================================
INSERT INTO station_rule (station_id, train_no, formation_length, track_no, entry_direction,
                          passenger_floor, recommended_gate, walking_route, walking_minutes, priority) VALUES
(8, 'G6001', 8,  '5',  '北', '2F', 'A12', '2F北进站口→东安检区→A12检票口→5站台', 6,  10),
(8, 'G6001', 8,  '5',  '南', '1F', 'A12', '1F南进站口→中央扶梯上2F→A12检票口→5站台', 8, 5),
(8, 'G1101', 16, '9',  '北', '2F', 'B08', '2F北进站口→西安检区→B08检票口→9站台', 7,  10),
(8, 'G1101', 16, '9',  '南', '3F', 'B06', '3F餐饮区→西侧扶梯下2F→B06检票口→9站台', 9, 5),
(8, 'G1101', 16, '9',  '南', '1F', 'B08', '1F南进站口→中央扶梯上2F→B08检票口→9站台', 8, 3),
(8, 'G6002', 8,  '3',  '北', '2F', 'A06', '2F北进站口→东安检区→A06检票口→3站台', 5,  10),
(8, 'K599',  18, '12', '南', '1F', 'C03', '1F南进站口→东侧通道→C03检票口→12站台', 12, 10),
(8, 'K599',  18, '12', '北', '2F', 'C05', '2F北进站口→东侧扶梯下1F→C05检票口→12站台', 13, 5),
(8, 'G1102', 16, '7',  '北', '2F', 'B12', '2F北进站口→西安检区→B12检票口→7站台', 6,  10),
(8, 'G1102', 16, '7',  '南', '1F', 'B10', '1F南进站口→中央扶梯上2F→B10检票口→7站台', 9, 5);

-- =====================================================================
-- 查询示例 1：余票查询（席位复用核心 SQL）
-- 北京南(order 1) → 上海虹桥(order 4)，G1，2026-04-15，二等座
-- 原理：取区间内所有原子区段余票的最小值
-- =====================================================================
SELECT MIN(remaining_count) AS remaining
FROM seat_inventory
WHERE train_id = 1
  AND travel_date = '2026-04-15'
  AND seat_type = '二等座'
  AND from_station_order >= 1
  AND to_station_order <= 4;

-- 带站名的完整余票查询（联表）
SELECT t.train_no,
       fs.station_name AS from_station,
       ts.station_name AS to_station,
       MIN(i.remaining_count) AS remaining,
       SUM(i.price)           AS total_price
FROM seat_inventory i
         JOIN train t ON t.id = i.train_id
         JOIN train_station tfs ON tfs.train_id = i.train_id AND tfs.station_order = 1
         JOIN station fs ON fs.id = tfs.station_id
         JOIN train_station tts ON tts.train_id = i.train_id AND tts.station_order = 4
         JOIN station ts ON ts.id = tts.station_id
WHERE i.train_id = 1
  AND i.travel_date = '2026-04-15'
  AND i.seat_type = '二等座'
  AND i.from_station_order >= 1
  AND i.to_station_order <= 4
GROUP BY t.train_no, fs.station_name, ts.station_name;

-- =====================================================================
-- 查询示例 2：候补聚合（按出发站、到达站、日期分组统计）
-- =====================================================================
SELECT c.travel_date,
       fs.station_name AS from_station,
       ts.station_name AS to_station,
       c.seat_type,
       COUNT(*)        AS candidate_count,
       MIN(c.candidate_time) AS first_candidate_time
FROM candidate c
         JOIN station fs ON fs.id = c.from_station_id
         JOIN station ts ON ts.id = c.to_station_id
WHERE c.status = 'WAITING'
GROUP BY c.travel_date, fs.station_name, ts.station_name, c.seat_type
HAVING COUNT(*) >= 3
ORDER BY candidate_count DESC, c.travel_date;

-- =====================================================================
-- 查询示例 3：扣减库存（区间内所有原子区段同时扣减，事务内执行）
-- 演示用 ROLLBACK，不实际修改样例数据；真实下单在第4章实现
-- =====================================================================
START TRANSACTION;
SELECT remaining_count
FROM seat_inventory
WHERE train_id = 1
  AND travel_date = '2026-04-15'
  AND seat_type = '二等座'
  AND from_station_order >= 1
  AND to_station_order <= 4
FOR UPDATE;

UPDATE seat_inventory
SET remaining_count = remaining_count - 1,
    version = version + 1
WHERE train_id = 1
  AND travel_date = '2026-04-15'
  AND seat_type = '二等座'
  AND from_station_order >= 1
  AND to_station_order <= 4
  AND remaining_count >= 1;
ROLLBACK;

-- =====================================================================
-- 查询示例 4：事务与锁演示（可重复读下的幻读、间隙锁）
-- 会话A：加锁读不存在的区间
-- =====================================================================
START TRANSACTION;
SELECT *
FROM candidate
WHERE train_id = 7
  AND travel_date = '2026-04-17'
  AND status = 'WAITING'
FOR UPDATE;

-- 会话B（另一连接）执行下面语句会被间隙锁阻塞，直到会话A提交：
-- INSERT INTO candidate (candidate_no, user_id, train_id, travel_date, from_station_id, to_station_id,
--                        from_station_order, to_station_order, seat_type)
-- VALUES ('CAND202604170001', 3001, 7, '2026-04-17', 8, 12, 1, 3, '二等座');
-- 脚本内回滚，避免演示事务长期持有间隙锁
ROLLBACK;

-- =====================================================================
-- 查询示例 5：索引优化前后对比（EXPLAIN 分析见设计文档）
-- 场景：候补聚合查询在无索引时的全表扫描
-- 复现步骤：
--   1) ALTER TABLE candidate DROP INDEX idx_agg;
--   2) EXPLAIN SELECT ... （type=ALL，全表扫描）
--   3) ALTER TABLE candidate ADD INDEX idx_agg (travel_date, from_station_id, to_station_id, status);
--   4) EXPLAIN SELECT ... （type=ref，Using index）
-- =====================================================================

ANALYZE TABLE station, train, train_station, seat_inventory, ticket_order, candidate, station_rule;
