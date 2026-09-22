package com.railway.ruleengine.rule;

import com.railway.ruleengine.StationRuleContext;
import com.railway.ruleengine.StationRuleHandler;
import org.springframework.stereotype.Component;

@Component
public class EntryDirectionRule implements StationRuleHandler {

    @Override
    public int order() {
        return 30;
    }

    @Override
    public String ruleName() {
        return "进站方向规则";
    }

    @Override
    public void apply(StationRuleContext context) {
        String floor = context.getQuery().getPassengerFloor();
        String direction = context.getQuery().getEntryDirection();
        context.setEntrySegment(floor + direction + "进站口");
        if ("C".equals(context.getZone())) {
            context.setVerticalSegment("北".equals(direction) ? "东侧扶梯下1F" : "东侧通道");
        }
    }
}
