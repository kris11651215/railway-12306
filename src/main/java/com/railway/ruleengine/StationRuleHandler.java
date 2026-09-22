package com.railway.ruleengine;

public interface StationRuleHandler {

    int order();

    String ruleName();

    void apply(StationRuleContext context);
}
