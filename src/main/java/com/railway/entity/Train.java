package com.railway.entity;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Train extends RailwayEntity {

    private String trainNo;
    private TrainType trainType;
    private Direction direction;
    private String startStation;
    private String endStation;
    private String bureau;
    private LocalTime departureTime;
    private LocalTime arrivalTime;
    private int durationMinutes;
    private int mileage;
    private List<String> stopStations = new ArrayList<>();

    public Train() {
    }

    public Train(Long id, String trainNo, Direction direction, String startStation, String endStation,
                 String bureau, LocalTime departureTime, LocalTime arrivalTime, int durationMinutes,
                 int mileage, List<String> stopStations) {
        super(id);
        this.trainNo = trainNo;
        this.trainType = TrainType.fromTrainNo(trainNo);
        this.direction = direction;
        this.startStation = startStation;
        this.endStation = endStation;
        this.bureau = bureau;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.durationMinutes = durationMinutes;
        this.mileage = mileage;
        this.stopStations = new ArrayList<>(stopStations);
    }

    public String getTrainNo() {
        return trainNo;
    }

    public void setTrainNo(String trainNo) {
        this.trainNo = trainNo;
        this.trainType = TrainType.fromTrainNo(trainNo);
    }

    public TrainType getTrainType() {
        return trainType;
    }

    public void setTrainType(TrainType trainType) {
        this.trainType = trainType;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public String getStartStation() {
        return startStation;
    }

    public void setStartStation(String startStation) {
        this.startStation = startStation;
    }

    public String getEndStation() {
        return endStation;
    }

    public void setEndStation(String endStation) {
        this.endStation = endStation;
    }

    public String getBureau() {
        return bureau;
    }

    public void setBureau(String bureau) {
        this.bureau = bureau;
    }

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalTime departureTime) {
        this.departureTime = departureTime;
    }

    public LocalTime getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(LocalTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getMileage() {
        return mileage;
    }

    public void setMileage(int mileage) {
        this.mileage = mileage;
    }

    public List<String> getStopStations() {
        return stopStations;
    }

    public void setStopStations(List<String> stopStations) {
        this.stopStations = new ArrayList<>(stopStations);
    }

    @Override
    public RailwaySystem getSystemCategory() {
        return RailwaySystem.ROLLING_STOCK;
    }

    @Override
    public String toString() {
        return String.format("Train{no=%s, type=%s, direction=%s, route=%s→%s, depart=%s, arrive=%s, %dkm}",
                trainNo, trainType == null ? null : trainType.getChineseName(),
                direction == null ? null : direction.getChineseName(),
                startStation, endStation, departureTime, arrivalTime, mileage);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Train)) {
            return false;
        }
        Train train = (Train) o;
        return Objects.equals(trainNo, train.trainNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trainNo);
    }
}
