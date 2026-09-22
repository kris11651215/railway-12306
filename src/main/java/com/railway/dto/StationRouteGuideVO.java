package com.railway.dto;

import java.util.List;

public class StationRouteGuideVO {

    private String stationName;
    private String trainNo;
    private String zone;
    private String recommendedGate;
    private String walkingRoute;
    private int walkingMinutes;
    private List<String> matchedRules;
    private List<String> notes;

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public String getTrainNo() {
        return trainNo;
    }

    public void setTrainNo(String trainNo) {
        this.trainNo = trainNo;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getRecommendedGate() {
        return recommendedGate;
    }

    public void setRecommendedGate(String recommendedGate) {
        this.recommendedGate = recommendedGate;
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

    public void setMatchedRules(List<String> matchedRules) {
        this.matchedRules = matchedRules;
    }

    public List<String> getNotes() {
        return notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes;
    }
}
