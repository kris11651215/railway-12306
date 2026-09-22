package com.railway.console;

import com.railway.entity.Direction;
import com.railway.entity.Station;
import com.railway.entity.Train;
import com.railway.service.StationNotFoundException;
import com.railway.service.TrainQueryService;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ConsoleTrainQuery {

    public static void main(String[] args) {
        TrainQueryService service = buildMockService();
        if (args.length >= 2) {
            queryOnce(service, args[0], args[1]);
            return;
        }
        printWelcome(service);
        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                    System.out.println("已退出查询。");
                    break;
                }
                String[] parts = line.split("\\s+");
                if (parts.length < 2) {
                    System.out.println("格式：出发站 到达站（如：北京南 南京南）");
                    continue;
                }
                queryOnce(service, parts[0], parts[1]);
            }
        }
    }

    private static void queryOnce(TrainQueryService service, String from, String to) {
        try {
            Station fromStation = service.findStation(from)
                    .orElseThrow(() -> new StationNotFoundException(from));
            Station toStation = service.findStation(to)
                    .orElseThrow(() -> new StationNotFoundException(to));

            System.out.println();
            System.out.printf("出发站：%s（%s，%s，%s）%n",
                    fromStation.getStationName(), fromStation.getBureau(),
                    fromStation.getStationClass(), fromStation.getAffiliatedDepot());
            System.out.printf("到达站：%s（%s，%s，%s）%n",
                    toStation.getStationName(), toStation.getBureau(),
                    toStation.getStationClass(), toStation.getAffiliatedDepot());

            List<Train> result = service.query(from, to);
            if (result.isEmpty()) {
                System.out.printf("暂无 %s → %s 的直达车次，可考虑中转换乘（第5章实现）。%n", from, to);
                return;
            }

            System.out.printf("匹配车次（%s → %s）共 %d 趟：%n", from, to, result.size());
            for (Train train : result) {
                System.out.printf("%-6s %-6s %-4s 发 %s 到 %s  %dkm  %d分钟  担当：%s  系统：%s%n",
                        train.getTrainNo(),
                        train.getTrainType().getChineseName(),
                        train.getDirection().getChineseName(),
                        train.getDepartureTime(), train.getArrivalTime(),
                        train.getMileage(), train.getDurationMinutes(),
                        train.getBureau(), train.getSystemCategory().getChineseName());
                System.out.println("       经停：" + String.join(" > ", train.getStopStations()));
            }
        } catch (StationNotFoundException | IllegalArgumentException e) {
            System.out.println("查询失败：" + e.getMessage());
        }
    }

    private static void printWelcome(TrainQueryService service) {
        System.out.println("铁路智慧出行综合服务平台 - 控制台车次查询（第1章）");
        System.out.println("可用车站：" + String.join("、", service.stationNames()));
        System.out.println("输入：出发站 到达站（如：北京南 南京南），输入 exit 退出");
        System.out.println();
    }

    private static TrainQueryService buildMockService() {
        List<Station> stations = new ArrayList<>();
        stations.add(new Station(1L, "VNP", "北京南", "北京", "北京局集团", "北京动车段", "特等站", true));
        stations.add(new Station(2L, "BXP", "北京西", "北京", "北京局集团", "北京车辆段", "特等站", true));
        stations.add(new Station(3L, "JGK", "济南西", "济南", "济南局集团", "济南动车运用所", "一等站", false));
        stations.add(new Station(4L, "NKH", "南京南", "南京", "上海局集团", "南京动车段", "特等站", true));
        stations.add(new Station(5L, "AOH", "上海虹桥", "上海", "上海局集团", "上海动车段", "特等站", true));
        stations.add(new Station(6L, "SHH", "上海", "上海", "上海局集团", "上海车辆段", "特等站", true));
        stations.add(new Station(7L, "HGH", "杭州东", "杭州", "上海局集团", "杭州动车运用所", "一等站", false));
        stations.add(new Station(8L, "IZQ", "广州南", "广州", "广铁集团", "广州动车段", "特等站", true));
        stations.add(new Station(9L, "IOQ", "深圳北", "深圳", "广铁集团", "深圳动车运用所", "一等站", false));
        stations.add(new Station(10L, "IUQ", "虎门", "东莞", "广铁集团", "广州动车段", "三等站", false));
        stations.add(new Station(11L, "CWQ", "长沙南", "长沙", "广铁集团", "长沙动车运用所", "特等站", true));
        stations.add(new Station(12L, "WHN", "武汉", "武汉", "武汉局集团", "武汉动车段", "特等站", true));
        stations.add(new Station(13L, "ZAF", "郑州东", "郑州", "郑州局集团", "郑州动车段", "特等站", true));

        List<Train> trains = new ArrayList<>();
        trains.add(new Train(1L, "G1", Direction.DOWN, "北京南", "上海虹桥", "上海局集团",
                LocalTime.of(9, 0), LocalTime.of(13, 28), 268, 1318,
                List.of("北京南", "济南西", "南京南", "上海虹桥")));
        trains.add(new Train(2L, "G2", Direction.UP, "上海虹桥", "北京南", "上海局集团",
                LocalTime.of(7, 0), LocalTime.of(11, 29), 269, 1318,
                List.of("上海虹桥", "南京南", "济南西", "北京南")));
        trains.add(new Train(3L, "G11", Direction.DOWN, "北京南", "杭州东", "北京局集团",
                LocalTime.of(8, 0), LocalTime.of(12, 48), 288, 1480,
                List.of("北京南", "济南西", "南京南", "杭州东")));
        trains.add(new Train(4L, "G100", Direction.UP, "上海虹桥", "北京南", "上海局集团",
                LocalTime.of(14, 0), LocalTime.of(18, 28), 268, 1318,
                List.of("上海虹桥", "南京南", "济南西", "北京南")));
        trains.add(new Train(5L, "G6001", Direction.DOWN, "广州南", "深圳北", "广铁集团",
                LocalTime.of(8, 30), LocalTime.of(9, 2), 32, 102,
                List.of("广州南", "虎门", "深圳北")));
        trains.add(new Train(6L, "G6002", Direction.UP, "深圳北", "广州南", "广铁集团",
                LocalTime.of(9, 30), LocalTime.of(10, 5), 35, 102,
                List.of("深圳北", "虎门", "广州南")));
        trains.add(new Train(7L, "G1102", Direction.UP, "广州南", "武汉", "广铁集团",
                LocalTime.of(10, 0), LocalTime.of(13, 48), 228, 1069,
                List.of("广州南", "长沙南", "武汉")));
        trains.add(new Train(8L, "G1101", Direction.DOWN, "武汉", "广州南", "广铁集团",
                LocalTime.of(14, 0), LocalTime.of(17, 55), 235, 1069,
                List.of("武汉", "长沙南", "广州南")));
        trains.add(new Train(9L, "K599", Direction.DOWN, "北京西", "广州南", "广铁集团",
                LocalTime.of(12, 30), LocalTime.of(5, 10), 1000, 2298,
                List.of("北京西", "郑州东", "武汉", "长沙南", "广州南")));

        return new TrainQueryService(stations, trains);
    }
}
