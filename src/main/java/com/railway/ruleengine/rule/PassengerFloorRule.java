package com.railway.ruleengine.rule;

import com.railway.ruleengine.StationRuleContext;
import com.railway.ruleengine.StationRuleHandler;
import org.springframework.stereotype.Component;

@Component
public class PassengerFloorRule implements StationRuleHandler {

    @Override
    public int order() {
        return 40;
    }

    @Override
    public String ruleName() {
        return "旅客楼层规则";
    }

    @Override
    public void apply(StationRuleContext context) {
        String floor = context.getQuery().getPassengerFloor();
        boolean zoneC = "C".equals(context.getZone());
        switch (floor) {
            case "1F" -> {
                if (!zoneC) {
                    context.setVerticalSegment("中央扶梯上2F");
                }
                context.addExtraMinutes(2);
            }
            case "3F" -> {
                if (!zoneC) {
                    context.setEntrySegment("3F餐饮区");
                    context.setVerticalSegment("西侧扶梯下2F");
                }
                context.addExtraMinutes(2);
            }
            case "B1" -> {
                if (!zoneC) {
                    context.setEntrySegment("B1地铁层");
                    context.setVerticalSegment("地铁层扶梯上2F");
                }
                context.addExtraMinutes(3);
            }
            default -> {
            }
        }
    }
}
