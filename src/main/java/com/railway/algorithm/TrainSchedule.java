package com.railway.algorithm;

import java.util.List;

public class TrainSchedule {

    private final String trainNo;
    private final String trainType;
    private final List<TrainStop> stops;

    public TrainSchedule(String trainNo, String trainType, List<TrainStop> stops) {
        this.trainNo = trainNo;
        this.trainType = trainType;
        this.stops = List.copyOf(stops);
    }

    public String getTrainNo() {
        return trainNo;
    }

    public String getTrainType() {
        return trainType;
    }

    public List<TrainStop> getStops() {
        return stops;
    }
}
