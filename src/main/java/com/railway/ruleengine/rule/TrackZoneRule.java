package com.railway.ruleengine.rule;

import com.railway.common.ErrorCode;
import com.railway.exception.BusinessException;
import com.railway.ruleengine.StationRuleContext;
import com.railway.ruleengine.StationRuleHandler;
import org.springframework.stereotype.Component;

@Component
public class TrackZoneRule implements StationRuleHandler {

    private static final int MAX_TRACK = 18;

    @Override
    public int order() {
        return 10;
    }

    @Override
    public String ruleName() {
        return "股道分区规则";
    }

    @Override
    public void apply(StationRuleContext context) {
        int track = context.trackNumber();
        if (track < 1 || track > MAX_TRACK) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "股道号仅支持 1-" + MAX_TRACK + "：" + track);
        }
        if (track <= 6) {
            context.setZone("A");
            context.setGatePrefix("A");
            context.setGateCapacity(16);
            context.setSecurityChannel("东安检区");
            context.setBaseMinutes(4);
        } else if (track <= 11) {
            context.setZone("B");
            context.setGatePrefix("B");
            context.setGateCapacity(16);
            context.setSecurityChannel("西安检区");
            context.setBaseMinutes(5);
        } else {
            context.setZone("C");
            context.setGatePrefix("C");
            context.setGateCapacity(8);
            context.setSecurityChannel("东侧通道");
            context.setBaseMinutes(7);
        }
        context.setGateNumber(Math.min(track * 2, context.getGateCapacity()));
    }
}
