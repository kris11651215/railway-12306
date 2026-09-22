package com.railway.dto;

import java.time.LocalDateTime;

public class TravelGuideVO {

    private String trainNo;
    private String fromStation;
    private String userLocation;
    private String metroLine;
    private String metroExit;
    private String metroTip;
    private String entryPlatform;
    private String recommendedGate;
    private String walkingRoute;
    private int walkingMinutes;
    private String congestionLevel;
    private String congestionTip;
    private int suggestedArrivalMinutes;
    private LocalDateTime generatedAt;

    public String getTrainNo() {
        return trainNo;
    }

    public void setTrainNo(String trainNo) {
        this.trainNo = trainNo;
    }

    public String getFromStation() {
        return fromStation;
    }

    public void setFromStation(String fromStation) {
        this.fromStation = fromStation;
    }

    public String getUserLocation() {
        return userLocation;
    }

    public void setUserLocation(String userLocation) {
        this.userLocation = userLocation;
    }

    public String getMetroLine() {
        return metroLine;
    }

    public void setMetroLine(String metroLine) {
        this.metroLine = metroLine;
    }

    public String getMetroExit() {
        return metroExit;
    }

    public void setMetroExit(String metroExit) {
        this.metroExit = metroExit;
    }

    public String getMetroTip() {
        return metroTip;
    }

    public void setMetroTip(String metroTip) {
        this.metroTip = metroTip;
    }

    public String getEntryPlatform() {
        return entryPlatform;
    }

    public void setEntryPlatform(String entryPlatform) {
        this.entryPlatform = entryPlatform;
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

    public String getCongestionLevel() {
        return congestionLevel;
    }

    public void setCongestionLevel(String congestionLevel) {
        this.congestionLevel = congestionLevel;
    }

    public String getCongestionTip() {
        return congestionTip;
    }

    public void setCongestionTip(String congestionTip) {
        this.congestionTip = congestionTip;
    }

    public int getSuggestedArrivalMinutes() {
        return suggestedArrivalMinutes;
    }

    public void setSuggestedArrivalMinutes(int suggestedArrivalMinutes) {
        this.suggestedArrivalMinutes = suggestedArrivalMinutes;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
