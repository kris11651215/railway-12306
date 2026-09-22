package com.railway.algorithm;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SampleTimetable {

    private SampleTimetable() {
    }

    public static String segmentKey(String trainNo, int fromStationOrder) {
        return trainNo + "#" + fromStationOrder;
    }

    public static List<TrainSchedule> schedules() {
        return List.of(
                new TrainSchedule("G1", "G", List.of(
                        stop("北京南", 1, null, "09:00", 0, 0),
                        stop("济南西", 2, "10:22", "10:24", 0, 406),
                        stop("南京南", 3, "12:04", "12:06", 0, 1023),
                        stop("上海虹桥", 4, "13:28", null, 0, 1318))),
                new TrainSchedule("G2", "G", List.of(
                        stop("上海虹桥", 1, null, "07:00", 0, 0),
                        stop("南京南", 2, "08:23", "08:25", 0, 295),
                        stop("济南西", 3, "10:05", "10:07", 0, 912),
                        stop("北京南", 4, "11:29", null, 0, 1318))),
                new TrainSchedule("G11", "G", List.of(
                        stop("北京南", 1, null, "08:00", 0, 0),
                        stop("济南西", 2, "09:22", "09:24", 0, 406),
                        stop("南京南", 3, "11:04", "11:06", 0, 1023),
                        stop("杭州东", 4, "12:48", null, 0, 1480))),
                new TrainSchedule("G100", "G", List.of(
                        stop("上海虹桥", 1, null, "14:00", 0, 0),
                        stop("南京南", 2, "15:23", "15:25", 0, 295),
                        stop("济南西", 3, "17:05", "17:07", 0, 912),
                        stop("北京南", 4, "18:28", null, 0, 1318))),
                new TrainSchedule("G6001", "G", List.of(
                        stop("广州南", 1, null, "08:30", 0, 0),
                        stop("虎门", 2, "08:47", "08:48", 0, 50),
                        stop("深圳北", 3, "09:02", null, 0, 102))),
                new TrainSchedule("G6002", "G", List.of(
                        stop("深圳北", 1, null, "09:30", 0, 0),
                        stop("虎门", 2, "09:43", "09:44", 0, 50),
                        stop("广州南", 3, "10:05", null, 0, 102))),
                new TrainSchedule("G1102", "G", List.of(
                        stop("广州南", 1, null, "10:00", 0, 0),
                        stop("长沙南", 2, "12:03", "12:06", 0, 707),
                        stop("武汉", 3, "13:48", null, 0, 1069))),
                new TrainSchedule("G1101", "G", List.of(
                        stop("武汉", 1, null, "14:00", 0, 0),
                        stop("长沙南", 2, "15:41", "15:44", 0, 362),
                        stop("广州南", 3, "17:55", null, 0, 1069))),
                new TrainSchedule("K599", "K", List.of(
                        stop("北京西", 1, null, "12:30", 0, 0),
                        stop("郑州东", 2, "17:52", "18:00", 0, 693),
                        stop("武汉", 3, "21:30", "21:38", 0, 1229),
                        stop("长沙南", 4, "00:50", "00:58", 1, 1591),
                        stop("广州南", 5, "05:10", null, 1, 2298))));
    }

    public static Map<String, BigDecimal> segmentPrices() {
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        price(prices, "G1", 1, "232.50");
        price(prices, "G1", 2, "344.50");
        price(prices, "G1", 3, "139.50");
        price(prices, "G2", 1, "139.50");
        price(prices, "G2", 2, "344.50");
        price(prices, "G2", 3, "232.50");
        price(prices, "G11", 1, "232.50");
        price(prices, "G11", 2, "344.50");
        price(prices, "G11", 3, "220.50");
        price(prices, "G100", 1, "139.50");
        price(prices, "G100", 2, "344.50");
        price(prices, "G100", 3, "232.50");
        price(prices, "G6001", 1, "25.00");
        price(prices, "G6001", 2, "34.50");
        price(prices, "G6002", 1, "34.50");
        price(prices, "G6002", 2, "25.00");
        price(prices, "G1102", 1, "220.50");
        price(prices, "G1102", 2, "164.00");
        price(prices, "G1101", 1, "164.00");
        price(prices, "G1101", 2, "220.50");
        price(prices, "K599", 1, "105.00");
        price(prices, "K599", 2, "88.00");
        price(prices, "K599", 3, "76.00");
        price(prices, "K599", 4, "115.00");
        return prices;
    }

    public static Map<String, Integer> segmentRemaining() {
        Map<String, Integer> remaining = new LinkedHashMap<>();
        remainingOf(remaining, "G1", 1, 320);
        remainingOf(remaining, "G1", 2, 3);
        remainingOf(remaining, "G1", 3, 210);
        remainingOf(remaining, "G11", 1, 200);
        remainingOf(remaining, "G11", 2, 150);
        remainingOf(remaining, "G11", 3, 180);
        remainingOf(remaining, "G6001", 1, 80);
        remainingOf(remaining, "G6001", 2, 75);
        remainingOf(remaining, "G1102", 1, 260);
        remainingOf(remaining, "G1102", 2, 240);
        remainingOf(remaining, "K599", 1, 600);
        remainingOf(remaining, "K599", 2, 520);
        remainingOf(remaining, "K599", 3, 480);
        remainingOf(remaining, "K599", 4, 430);
        return remaining;
    }

    public static String bureauOf(String trainNo) {
        return switch (trainNo) {
            case "G11" -> "北京局集团";
            case "G6001", "G6002", "G1101", "G1102", "K599" -> "广铁集团";
            default -> "上海局集团";
        };
    }

    public static String seatTypeOf(String trainType) {
        return "K".equals(trainType) ? "硬座" : "二等座";
    }

    private static TrainStop stop(String stationName, int stationOrder, String arrivalTime,
                                  String departureTime, int dayOffset, int mileageFromStart) {
        return new TrainStop(stationName, stationOrder,
                arrivalTime == null ? null : LocalTime.parse(arrivalTime),
                departureTime == null ? null : LocalTime.parse(departureTime),
                dayOffset, mileageFromStart);
    }

    private static void price(Map<String, BigDecimal> prices, String trainNo, int order, String value) {
        prices.put(segmentKey(trainNo, order), new BigDecimal(value));
    }

    private static void remainingOf(Map<String, Integer> remaining, String trainNo, int order, int value) {
        remaining.put(segmentKey(trainNo, order), value);
    }
}
