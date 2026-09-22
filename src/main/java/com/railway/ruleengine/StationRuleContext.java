package com.railway.ruleengine;

import com.railway.common.ErrorCode;
import com.railway.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

public class StationRuleContext {

    private final StationRouteQuery query;
    private String zone;
    private String gatePrefix;
    private int gateNumber;
    private int gateCapacity = 16;
    private String securityChannel;
    private String entrySegment;
    private String verticalSegment;
    private int baseMinutes;
    private int extraMinutes;
    private String walkingRoute;
    private int walkingMinutes;
    private final List<String> matchedRules = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();

    public StationRuleContext(StationRouteQuery query) {
        this.query = query;
    }

    public StationRouteQuery getQuery() {
        return query;
    }

    public int trackNumber() {
        try {
            return Integer.parseInt(query.getTrackNo());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "股道号必须是数字：" + query.getTrackNo());
        }
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getGatePrefix() {
        return gatePrefix;
    }

    public void setGatePrefix(String gatePrefix) {
        this.gatePrefix = gatePrefix;
    }

    public int getGateNumber() {
        return gateNumber;
    }

    public void setGateNumber(int gateNumber) {
        this.gateNumber = gateNumber;
    }

    public int getGateCapacity() {
        return gateCapacity;
    }

    public void setGateCapacity(int gateCapacity) {
        this.gateCapacity = gateCapacity;
    }

    public String getSecurityChannel() {
        return securityChannel;
    }

    public void setSecurityChannel(String securityChannel) {
        this.securityChannel = securityChannel;
    }

    public String getEntrySegment() {
        return entrySegment;
    }

    public void setEntrySegment(String entrySegment) {
        this.entrySegment = entrySegment;
    }

    public String getVerticalSegment() {
        return verticalSegment;
    }

    public void setVerticalSegment(String verticalSegment) {
        this.verticalSegment = verticalSegment;
    }

    public int getBaseMinutes() {
        return baseMinutes;
    }

    public void setBaseMinutes(int baseMinutes) {
        this.baseMinutes = baseMinutes;
    }

    public int getExtraMinutes() {
        return extraMinutes;
    }

    public void addExtraMinutes(int minutes) {
        this.extraMinutes += minutes;
    }

    public String getWalkingRoute() {
        return walkingRoute;
    }

    public void setWalkingRoute(String walkingRoute) {
        this.walkingRoute = walkingRoute;
    }

    public int getWalkingMinutes() {
        return walkingMinutes;
    }

    public void setWalkingMinutes(int walkingMinutes) {
        this.walkingMinutes = walkingMinutes;
    }

    public List<String> getMatchedRules() {
        return matchedRules;
    }

    public void markRule(String ruleName) {
        this.matchedRules.add(ruleName);
    }

    public List<String> getNotes() {
        return notes;
    }

    public void addNote(String note) {
        this.notes.add(note);
    }
}
