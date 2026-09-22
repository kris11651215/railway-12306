package com.railway.ai.travel;

import com.railway.common.ErrorCode;
import com.railway.dto.TravelGuideVO;
import com.railway.exception.BusinessException;
import com.railway.ruleengine.StationRuleEngine;
import com.railway.ruleengine.rule.EntryDirectionRule;
import com.railway.ruleengine.rule.FormationLengthRule;
import com.railway.ruleengine.rule.PassengerFloorRule;
import com.railway.ruleengine.rule.TrackZoneRule;
import com.railway.ruleengine.rule.WalkTimeAuditRule;
import com.railway.service.StationRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelGuideServiceTest {

    private TravelGuideService service;

    @BeforeEach
    void setUp() {
        StationRuleEngine engine = new StationRuleEngine(List.of(
                new TrackZoneRule(),
                new FormationLengthRule(),
                new EntryDirectionRule(),
                new PassengerFloorRule(),
                new WalkTimeAuditRule()));
        service = new TravelGuideService(new StationRouteService(engine));
    }

    @Test
    void metroLineTwoAtMorningPeakShouldGiveGateAndCongestion() {
        TravelGuideVO guide = service.guide("G1102", "广州南", "地铁2号线",
                LocalDateTime.of(2026, 9, 15, 8, 0));
        assertEquals("地铁2号线", guide.getMetroLine());
        assertEquals("D 出口", guide.getMetroExit());
        assertEquals("B16", guide.getRecommendedGate());
        assertTrue(guide.getEntryPlatform().contains("西进站平台"));
        assertEquals("高峰", guide.getCongestionLevel());
        assertEquals(45, guide.getSuggestedArrivalMinutes());
        assertTrue(guide.getWalkingMinutes() > 0);
    }

    @Test
    void offPeakUnknownLocationShouldUseDefaultExit() {
        TravelGuideVO guide = service.guide("G6002", "广州南", "站前广场",
                LocalDateTime.of(2026, 9, 15, 14, 0));
        assertEquals("未识别接驳方式", guide.getMetroLine());
        assertEquals("B 出口", guide.getMetroExit());
        assertEquals("畅通", guide.getCongestionLevel());
        assertEquals(30, guide.getSuggestedArrivalMinutes());
    }

    @Test
    void taxiLocationShouldMapToPickupArea() {
        TravelGuideVO guide = service.guide("K599", "广州南", "网约车",
                LocalDateTime.of(2026, 9, 15, 18, 0));
        assertEquals("出租车/网约车", guide.getMetroLine());
        assertEquals("P1 快速接客区", guide.getMetroExit());
        assertEquals("高峰", guide.getCongestionLevel());
        assertEquals("C08", guide.getRecommendedGate());
    }

    @Test
    void missingTrainNoShouldFailWithParamError() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.guide("", "广州南", "地铁2号线"));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }
}
