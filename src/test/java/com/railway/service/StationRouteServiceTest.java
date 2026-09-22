package com.railway.service;

import com.railway.common.ErrorCode;
import com.railway.dto.StationRouteGuideVO;
import com.railway.exception.BusinessException;
import com.railway.ruleengine.StationRuleEngine;
import com.railway.ruleengine.rule.EntryDirectionRule;
import com.railway.ruleengine.rule.FormationLengthRule;
import com.railway.ruleengine.rule.PassengerFloorRule;
import com.railway.ruleengine.rule.TrackZoneRule;
import com.railway.ruleengine.rule.WalkTimeAuditRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StationRouteServiceTest {

    private StationRouteService service;

    @BeforeEach
    void setUp() {
        StationRuleEngine engine = new StationRuleEngine(List.of(
                new TrackZoneRule(),
                new FormationLengthRule(),
                new EntryDirectionRule(),
                new PassengerFloorRule(),
                new WalkTimeAuditRule()));
        service = new StationRouteService(engine);
    }

    @Test
    void aliasesShouldBeNormalized() {
        StationRouteGuideVO vo = service.guide(null, "G1101", 16, "9", "north", "2f");
        assertEquals("广州南", vo.getStationName());
        assertEquals("B16", vo.getRecommendedGate());
    }

    @Test
    void southOnFirstFloorAliasShouldNormalize() {
        StationRouteGuideVO vo = service.guide("广州南", "G1101", 16, "9", "SOUTH", "F1");
        assertEquals("1F南进站口→中央扶梯上2F→B16检票口→9站台", vo.getWalkingRoute());
    }

    @Test
    void missingTrainNoShouldFailWithParamError() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.guide("广州南", null, 16, "9", "北", "2F"));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void invalidFormationLengthShouldFailWithParamError() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.guide("广州南", "G1101", 0, "9", "北", "2F"));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void invalidDirectionShouldFailWithParamError() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.guide("广州南", "G1101", 16, "9", "UP", "2F"));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void invalidFloorShouldFailWithParamError() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.guide("广州南", "G1101", 16, "9", "北", "4F"));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }
}
