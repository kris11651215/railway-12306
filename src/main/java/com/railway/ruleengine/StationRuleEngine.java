package com.railway.ruleengine;

import com.railway.dto.StationRouteGuideVO;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class StationRuleEngine {

    private final List<StationRuleHandler> handlers;

    public StationRuleEngine(List<StationRuleHandler> handlers) {
        this.handlers = handlers.stream()
                .sorted(Comparator.comparingInt(StationRuleHandler::order))
                .toList();
    }

    public StationRouteGuideVO guide(StationRouteQuery query) {
        StationRuleContext context = new StationRuleContext(query);
        for (StationRuleHandler handler : handlers) {
            handler.apply(context);
            context.markRule(handler.ruleName());
        }
        StationRouteGuideVO vo = new StationRouteGuideVO();
        vo.setStationName(query.getStationName());
        vo.setTrainNo(query.getTrainNo());
        vo.setZone(context.getZone());
        vo.setRecommendedGate(context.getGatePrefix() + String.format("%02d", context.getGateNumber()));
        vo.setWalkingRoute(context.getWalkingRoute());
        vo.setWalkingMinutes(context.getWalkingMinutes());
        vo.setMatchedRules(List.copyOf(context.getMatchedRules()));
        vo.setNotes(List.copyOf(context.getNotes()));
        return vo;
    }
}
