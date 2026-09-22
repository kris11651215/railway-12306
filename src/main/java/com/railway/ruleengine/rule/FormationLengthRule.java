package com.railway.ruleengine.rule;

import com.railway.ruleengine.StationRuleContext;
import com.railway.ruleengine.StationRuleHandler;
import org.springframework.stereotype.Component;

@Component
public class FormationLengthRule implements StationRuleHandler {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String ruleName() {
        return "编组长度规则";
    }

    @Override
    public void apply(StationRuleContext context) {
        int formationLength = context.getQuery().getFormationLength();
        int offset;
        int extraMinutes;
        if (formationLength <= 8) {
            offset = -2;
            extraMinutes = 0;
        } else if (formationLength <= 16) {
            offset = 2;
            extraMinutes = 1;
        } else {
            offset = 4;
            extraMinutes = 2;
        }
        int adjusted = context.getGateNumber() + offset;
        int clamped = Math.max(1, Math.min(context.getGateCapacity(), adjusted));
        if (clamped != adjusted) {
            context.addNote("检票口已按股道容量收紧至 " + context.getGatePrefix() + String.format("%02d", clamped));
        }
        context.setGateNumber(clamped);
        context.addExtraMinutes(extraMinutes);
    }
}
