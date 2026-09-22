package com.railway.ai.travel;

import com.railway.common.ErrorCode;
import com.railway.dto.StationRouteGuideVO;
import com.railway.dto.TravelGuideVO;
import com.railway.exception.BusinessException;
import com.railway.service.StationRouteService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

@Service
public class TravelGuideService {

    private static final Map<String, int[]> PLATFORM_CATALOG = Map.of(
            "G6001", new int[]{8, 5},
            "G6002", new int[]{8, 3},
            "G1101", new int[]{16, 9},
            "G1102", new int[]{16, 7},
            "K599", new int[]{18, 12});

    private final StationRouteService stationRouteService;

    public TravelGuideService(StationRouteService stationRouteService) {
        this.stationRouteService = stationRouteService;
    }

    public TravelGuideVO guide(String trainNo, String fromStation, String userLocation) {
        return guide(trainNo, fromStation, userLocation, LocalDateTime.now());
    }

    public TravelGuideVO guide(String trainNo, String fromStation, String userLocation, LocalDateTime now) {
        if (trainNo == null || trainNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "trainNo 为必填参数");
        }
        if (fromStation == null || fromStation.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "fromStation 为必填参数");
        }
        String actualLocation = userLocation == null || userLocation.isBlank() ? "未提供位置" : userLocation.trim();
        int[] platform = PLATFORM_CATALOG.getOrDefault(trainNo.trim(), new int[]{16, 9});
        StationRouteGuideVO gate = stationRouteService.guide(fromStation.trim(), trainNo.trim(),
                platform[0], String.valueOf(platform[1]), "北", "2F");
        Metro metro = resolveMetro(actualLocation);
        Congestion congestion = resolveCongestion(now.toLocalTime());

        TravelGuideVO vo = new TravelGuideVO();
        vo.setTrainNo(trainNo.trim());
        vo.setFromStation(fromStation.trim());
        vo.setUserLocation(actualLocation);
        vo.setMetroLine(metro.line);
        vo.setMetroExit(metro.exit);
        vo.setMetroTip(metro.tip);
        vo.setEntryPlatform(entryPlatform(gate.getZone()));
        vo.setRecommendedGate(gate.getRecommendedGate());
        vo.setWalkingRoute(gate.getWalkingRoute());
        vo.setWalkingMinutes(gate.getWalkingMinutes());
        vo.setCongestionLevel(congestion.level);
        vo.setCongestionTip(congestion.tip);
        vo.setSuggestedArrivalMinutes(congestion.arrivalMinutes);
        vo.setGeneratedAt(now);
        return vo;
    }

    private String entryPlatform(String zone) {
        if ("A".equals(zone)) {
            return "2F 东进站平台（A 区检票口）";
        }
        if ("B".equals(zone)) {
            return "2F 西进站平台（B 区检票口）";
        }
        return "1F 东侧进站平台（C 区检票口）";
    }

    private Metro resolveMetro(String userLocation) {
        if (userLocation.contains("2号线") || userLocation.contains("地铁2") || userLocation.contains("地铁二")) {
            return new Metro("地铁2号线", "D 出口", "出闸后经中央扶梯上 2F 进站平台");
        }
        if (userLocation.contains("7号线") || userLocation.contains("地铁7") || userLocation.contains("地铁七")) {
            return new Metro("地铁7号线", "H 出口", "出闸后沿东侧通道前往 2F 进站平台");
        }
        if (userLocation.contains("公交")) {
            return new Metro("公交站场", "B 出口", "公交落客后步行约 8 分钟进站");
        }
        if (userLocation.contains("出租") || userLocation.contains("网约")) {
            return new Metro("出租车/网约车", "P1 快速接客区", "落客平台直行进入 1F 南进站口");
        }
        if (userLocation.contains("停车") || userLocation.contains("自驾")) {
            return new Metro("自驾", "P3 停车场", "停车后经 1F 通道进站，高峰建议提前出发");
        }
        return new Metro("未识别接驳方式", "B 出口", "请以站内导向标识为准，或咨询车站工作人员");
    }

    private Congestion resolveCongestion(LocalTime time) {
        if (isBetween(time, LocalTime.of(7, 0), LocalTime.of(9, 30))) {
            return new Congestion("高峰", "早高峰，地铁出站与东安检区客流大，建议预留 45 分钟", 45);
        }
        if (isBetween(time, LocalTime.of(17, 0), LocalTime.of(19, 30))) {
            return new Congestion("高峰", "晚高峰，西安检区与检票口客流集中，建议预留 45 分钟", 45);
        }
        if (isBetween(time, LocalTime.of(11, 0), LocalTime.of(13, 30))) {
            return new Congestion("平峰", "排队约 5 分钟，建议提前 30 分钟到站", 30);
        }
        return new Congestion("畅通", "当前客流较小，建议提前 30 分钟到站", 30);
    }

    private boolean isBetween(LocalTime time, LocalTime start, LocalTime end) {
        return !time.isBefore(start) && !time.isAfter(end);
    }

    private static class Metro {

        private final String line;
        private final String exit;
        private final String tip;

        Metro(String line, String exit, String tip) {
            this.line = line;
            this.exit = exit;
            this.tip = tip;
        }
    }

    private static class Congestion {

        private final String level;
        private final String tip;
        private final int arrivalMinutes;

        Congestion(String level, String tip, int arrivalMinutes) {
            this.level = level;
            this.tip = tip;
            this.arrivalMinutes = arrivalMinutes;
        }
    }
}
