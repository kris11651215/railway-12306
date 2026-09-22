package com.railway.ruleengine;

import com.railway.common.ErrorCode;
import com.railway.dto.StationRouteGuideVO;
import com.railway.exception.BusinessException;
import com.railway.ruleengine.rule.EntryDirectionRule;
import com.railway.ruleengine.rule.FormationLengthRule;
import com.railway.ruleengine.rule.PassengerFloorRule;
import com.railway.ruleengine.rule.TrackZoneRule;
import com.railway.ruleengine.rule.WalkTimeAuditRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationRuleEngineTest {

    private final StationRuleEngine engine = engineWith(new ArrayList<>(List.of(
            new TrackZoneRule(),
            new FormationLengthRule(),
            new EntryDirectionRule(),
            new PassengerFloorRule(),
            new WalkTimeAuditRule())));

    @Test
    void g1101FromNorthOnSecondFloorShouldUseGateB() {
        StationRouteGuideVO vo = engine.guide(new StationRouteQuery("广州南", "G1101", 16, "9", "北", "2F"));
        assertEquals("B", vo.getZone());
        assertEquals("B16", vo.getRecommendedGate());
        assertEquals("2F北进站口→西安检区→B16检票口→9站台", vo.getWalkingRoute());
        assertEquals(6, vo.getWalkingMinutes());
        assertEquals(5, vo.getMatchedRules().size());
        assertTrue(vo.getNotes().stream().anyMatch(note -> note.contains("收紧")));
    }

    @Test
    void secondFloorNorthShortFormationShouldUseEastSecurityChannel() {
        StationRouteGuideVO vo = engine.guide(new StationRouteQuery("广州南", "G6002", 8, "3", "北", "2F"));
        assertEquals("A04", vo.getRecommendedGate());
        assertEquals("2F北进站口→东安检区→A04检票口→3站台", vo.getWalkingRoute());
        assertEquals(4, vo.getWalkingMinutes());
    }

    @Test
    void firstFloorSouthShouldAddEscalatorAndMinutes() {
        StationRouteGuideVO vo = engine.guide(new StationRouteQuery("广州南", "G1101", 16, "9", "南", "1F"));
        assertEquals("1F南进站口→中央扶梯上2F→B16检票口→9站台", vo.getWalkingRoute());
        assertEquals(8, vo.getWalkingMinutes());
    }

    @Test
    void longFormationOnTrackTwelveShouldUseGateC() {
        StationRouteGuideVO vo = engine.guide(new StationRouteQuery("广州南", "K599", 18, "12", "南", "1F"));
        assertEquals("C", vo.getZone());
        assertEquals("C08", vo.getRecommendedGate());
        assertEquals("1F南进站口→东侧通道→C08检票口→12站台", vo.getWalkingRoute());
        assertEquals(11, vo.getWalkingMinutes());
    }

    @Test
    void newRuleShouldExtendEngineWithoutTouchingCoreHandlers() {
        List<StationRuleHandler> handlers = new ArrayList<>(List.of(
                new TrackZoneRule(),
                new FormationLengthRule(),
                new EntryDirectionRule(),
                new PassengerFloorRule(),
                new WalkTimeAuditRule()));
        handlers.add(new StationRuleHandler() {
            @Override
            public int order() {
                return 45;
            }

            @Override
            public String ruleName() {
                return "高峰拥堵规则";
            }

            @Override
            public void apply(StationRuleContext context) {
                context.addExtraMinutes(2);
                context.addNote("高峰拥堵，加 2 分钟缓冲");
            }
        });
        StationRuleEngine extended = engineWith(handlers);
        StationRouteGuideVO vo = extended.guide(new StationRouteQuery("广州南", "G1101", 16, "9", "北", "2F"));
        assertEquals(8, vo.getWalkingMinutes());
        assertTrue(vo.getMatchedRules().contains("高峰拥堵规则"));
        assertEquals("2F北进站口→西安检区→B16检票口→9站台", vo.getWalkingRoute());
    }

    @Test
    void invalidTrackShouldFailWithParamError() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> engine.guide(new StationRouteQuery("广州南", "G1", 16, "25", "北", "2F")));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void nonNumericTrackShouldFailWithParamError() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> engine.guide(new StationRouteQuery("广州南", "G1", 16, "九", "北", "2F")));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    private StationRuleEngine engineWith(List<StationRuleHandler> handlers) {
        return new StationRuleEngine(handlers);
    }
}
