package com.railway.service;

import com.railway.algorithm.AStarRoutePlanner;
import com.railway.algorithm.DijkstraRoutePlanner;
import com.railway.algorithm.MemoryTransferGraphProvider;
import com.railway.algorithm.RouteStrategy;
import com.railway.common.ErrorCode;
import com.railway.config.RouteProperties;
import com.railway.dto.TransferPlanVO;
import com.railway.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferRouteServiceTest {

    private TransferRouteService service;
    private RouteProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RouteProperties();
        service = new TransferRouteService(new MemoryTransferGraphProvider(properties),
                new DijkstraRoutePlanner(), new AStarRoutePlanner(), properties);
    }

    @Test
    void shanghaiHongqiaoToHangzhouEastShouldTransferAtNanjingSouth() {
        TransferPlanVO plan = service.plan("上海虹桥", "杭州东", "fastest", LocalDate.of(2026, 4, 15));
        assertEquals(2, plan.getLegs().size());
        assertEquals("G2", plan.getLegs().get(0).getTrainNo());
        assertEquals("G11", plan.getLegs().get(1).getTrainNo());
        assertEquals(List.of("南京南"), plan.getTransferStations());
        assertEquals(348, plan.getTotalDurationMinutes());
        assertEquals(163, plan.getLegs().get(1).getWaitBeforeMinutes());
        assertTrue(plan.getLegs().stream().noneMatch(leg -> leg.getFromStation().contains("北京")
                || leg.getToStation().contains("广州")));
    }

    @Test
    void humenToShenzhenNorthShouldBeDirect() {
        TransferPlanVO plan = service.plan("虎门", "深圳北", "fastest", LocalDate.of(2026, 4, 15));
        assertEquals(1, plan.getLegs().size());
        assertEquals("G6001", plan.getLegs().get(0).getTrainNo());
        assertEquals(14, plan.getTotalDurationMinutes());
        assertEquals(0, plan.getTransferCount());
    }

    @Test
    void wuhanToGuangzhouSouthShouldBeDirect() {
        TransferPlanVO plan = service.plan("武汉", "广州南", "fastest", LocalDate.of(2026, 4, 15));
        assertEquals(1, plan.getLegs().size());
        assertEquals("G1101", plan.getLegs().get(0).getTrainNo());
        assertEquals(235, plan.getTotalDurationMinutes());
    }

    @Test
    void directRouteShouldKeepSingleLeg() {
        TransferPlanVO plan = service.plan("北京南", "上海虹桥", "fastest", LocalDate.of(2026, 4, 15));
        assertEquals(1, plan.getLegs().size());
        assertEquals("G1", plan.getLegs().get(0).getTrainNo());
        assertEquals(0, plan.getTransferCount());
        assertTrue(plan.getTransferStations().isEmpty());
    }

    @Test
    void cheapestShouldNotCostMoreThanFastest() {
        TransferPlanVO fastest = service.plan("上海虹桥", "杭州东", "fastest", LocalDate.of(2026, 4, 15));
        TransferPlanVO cheapest = service.plan("上海虹桥", "杭州东", "cheapest", LocalDate.of(2026, 4, 15));
        assertTrue(cheapest.getTotalPrice().compareTo(fastest.getTotalPrice()) <= 0);
    }

    @Test
    void astarAlgorithmShouldReturnSamePlan() {
        properties.setAlgorithm("astar");
        TransferPlanVO plan = service.plan("上海虹桥", "杭州东", "fastest", LocalDate.of(2026, 4, 15));
        assertEquals("astar", plan.getAlgorithm());
        assertEquals(348, plan.getTotalDurationMinutes());
    }

    @Test
    void unknownStationShouldFailWithStationNotFound() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.plan("上海", "苏州", "fastest", LocalDate.of(2026, 4, 15)));
        assertEquals(ErrorCode.STATION_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void noRouteShouldFailWithRouteNotFound() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.plan("郑州东", "杭州东", "fastest", LocalDate.of(2026, 4, 15)));
        assertEquals(ErrorCode.ROUTE_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void invalidStrategyShouldFailWithParamError() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.plan("北京南", "上海虹桥", "quickest", LocalDate.of(2026, 4, 15)));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void sameStationShouldFailWithParamError() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.plan("北京南", "北京南", "fastest", LocalDate.of(2026, 4, 15)));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void strategyEnumShouldParseCodes() {
        assertEquals(RouteStrategy.FASTEST, RouteStrategy.fromCode("fastest"));
        assertEquals(RouteStrategy.CHEAPEST, RouteStrategy.fromCode("CHEAPEST"));
        assertEquals(RouteStrategy.FASTEST, RouteStrategy.fromCode(null));
    }
}
