package com.railway.service;

import com.railway.common.ErrorCode;
import com.railway.dto.StationRouteGuideVO;
import com.railway.exception.BusinessException;
import com.railway.ruleengine.StationRouteQuery;
import com.railway.ruleengine.StationRuleEngine;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class StationRouteService {

    private static final String DEFAULT_STATION = "广州南";
    private static final Set<String> DIRECTIONS = Set.of("北", "南");
    private static final Map<String, String> DIRECTION_ALIASES = Map.of(
            "NORTH", "北", "N", "北", "北方", "北", "北向", "北",
            "SOUTH", "南", "S", "南", "南方", "南", "南向", "南");
    private static final Map<String, String> FLOOR_ALIASES = Map.of(
            "2F", "2F", "F2", "2F",
            "1F", "1F", "F1", "1F",
            "3F", "3F", "F3", "3F",
            "B1", "B1", "-1F", "B1");

    private final StationRuleEngine stationRuleEngine;

    public StationRouteService(StationRuleEngine stationRuleEngine) {
        this.stationRuleEngine = stationRuleEngine;
    }

    public StationRouteGuideVO guide(String station, String trainNo, Integer formationLength,
                                     String trackNo, String entryDirection, String passengerFloor) {
        if (trainNo == null || trainNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "trainNo 为必填参数");
        }
        if (formationLength == null || formationLength <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "formationLength 必须是正整数");
        }
        if (trackNo == null || trackNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "trackNo 为必填参数");
        }
        String direction = normalizeDirection(entryDirection);
        String floor = normalizeFloor(passengerFloor);
        String actualStation = (station == null || station.isBlank()) ? DEFAULT_STATION : station.trim();
        StationRouteQuery query = new StationRouteQuery(actualStation, trainNo.trim(),
                formationLength, trackNo.trim(), direction, floor);
        return stationRuleEngine.guide(query);
    }

    private String normalizeDirection(String entryDirection) {
        if (entryDirection == null || entryDirection.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "entryDirection 为必填参数，仅支持 北/南");
        }
        String upper = entryDirection.trim().toUpperCase();
        String direction = DIRECTIONS.contains(upper) ? upper : DIRECTION_ALIASES.get(upper);
        if (direction == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "entryDirection 仅支持 北/南：" + entryDirection);
        }
        return direction;
    }

    private String normalizeFloor(String passengerFloor) {
        if (passengerFloor == null || passengerFloor.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "passengerFloor 为必填参数，仅支持 2F/1F/3F/B1");
        }
        String upper = passengerFloor.trim().toUpperCase();
        String floor = FLOOR_ALIASES.get(upper);
        if (floor == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "passengerFloor 仅支持 2F/1F/3F/B1：" + passengerFloor);
        }
        return floor;
    }
}
