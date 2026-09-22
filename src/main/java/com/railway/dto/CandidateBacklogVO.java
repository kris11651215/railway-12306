package com.railway.dto;

import java.time.LocalDateTime;

public class CandidateBacklogVO {

    private String fromStation;
    private String toStation;
    private String seatType;
    private Integer waitingCount;
    private LocalDateTime firstCandidateTime;

    public String getFromStation() {
        return fromStation;
    }

    public void setFromStation(String fromStation) {
        this.fromStation = fromStation;
    }

    public String getToStation() {
        return toStation;
    }

    public void setToStation(String toStation) {
        this.toStation = toStation;
    }

    public String getSeatType() {
        return seatType;
    }

    public void setSeatType(String seatType) {
        this.seatType = seatType;
    }

    public Integer getWaitingCount() {
        return waitingCount;
    }

    public void setWaitingCount(Integer waitingCount) {
        this.waitingCount = waitingCount;
    }

    public LocalDateTime getFirstCandidateTime() {
        return firstCandidateTime;
    }

    public void setFirstCandidateTime(LocalDateTime firstCandidateTime) {
        this.firstCandidateTime = firstCandidateTime;
    }
}
