package com.railway.ruleengine.rule;

import com.railway.ruleengine.StationRuleContext;
import com.railway.ruleengine.StationRuleHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WalkTimeAuditRule implements StationRuleHandler {

    private static final int MIN_MINUTES = 4;
    private static final int MAX_MINUTES = 18;

    @Override
    public int order() {
        return 50;
    }

    @Override
    public String ruleName() {
        return "步行时间核算规则";
    }

    @Override
    public void apply(StationRuleContext context) {
        String floor = context.getQuery().getPassengerFloor();
        boolean securityVisible = "2F".equals(floor) && !"C".equals(context.getZone());
        List<String> segments = new ArrayList<>();
        segments.add(context.getEntrySegment());
        if (securityVisible && context.getSecurityChannel() != null) {
            segments.add(context.getSecurityChannel());
        }
        if (context.getVerticalSegment() != null) {
            segments.add(context.getVerticalSegment());
        }
        segments.add(context.getGatePrefix() + String.format("%02d", context.getGateNumber()) + "检票口");
        segments.add(context.trackNumber() + "站台");
        context.setWalkingRoute(String.join("→", segments));

        int minutes = context.getBaseMinutes() + context.getExtraMinutes();
        if (minutes < MIN_MINUTES) {
            minutes = MIN_MINUTES;
        }
        if (minutes > MAX_MINUTES) {
            minutes = MAX_MINUTES;
            context.addNote("步行时间已按站内上限收紧，请预留缓冲时间");
        }
        context.setWalkingMinutes(minutes);
    }
}
