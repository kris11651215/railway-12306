package com.railway.dto;

import java.time.LocalDate;

public class OrderCreateRequest {

    private Long userId;
    private String trainNo;
    private LocalDate travelDate;
    private String fromStation;
    private String toStation;
    private String seatType;
    private String passengerName;
    private String passengerIdCard;
    private Boolean failAfterDeduct;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTrainNo() {
        return trainNo;
    }

    public void setTrainNo(String trainNo) {
        this.trainNo = trainNo;
    }

    public LocalDate getTravelDate() {
        return travelDate;
    }

    public void setTravelDate(LocalDate travelDate) {
        this.travelDate = travelDate;
    }

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

    public String getPassengerName() {
        return passengerName;
    }

    public void setPassengerName(String passengerName) {
        this.passengerName = passengerName;
    }

    public String getPassengerIdCard() {
        return passengerIdCard;
    }

    public void setPassengerIdCard(String passengerIdCard) {
        this.passengerIdCard = passengerIdCard;
    }

    public Boolean getFailAfterDeduct() {
        return failAfterDeduct;
    }

    public void setFailAfterDeduct(Boolean failAfterDeduct) {
        this.failAfterDeduct = failAfterDeduct;
    }
}
