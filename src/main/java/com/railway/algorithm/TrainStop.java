package com.railway.algorithm;

import java.time.LocalTime;

public class TrainStop {

    private final String stationName;
    private final int stationOrder;
    private final LocalTime arrivalTime;
    private final LocalTime departureTime;
    private final int dayOffset;
    private final int mileageFromStart;

    public TrainStop(String stationName, int stationOrder, LocalTime arrivalTime,
                     LocalTime departureTime, int dayOffset, int mileageFromStart) {
        this.stationName = stationName;
        this.stationOrder = stationOrder;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
        this.dayOffset = dayOffset;
        this.mileageFromStart = mileageFromStart;
    }

    public String getStationName() {
        return stationName;
    }

    public int getStationOrder() {
        return stationOrder;
    }

    public LocalTime getArrivalTime() {
        return arrivalTime;
    }

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public int getDayOffset() {
        return dayOffset;
    }

    public int getMileageFromStart() {
        return mileageFromStart;
    }

    public int arrivalMinuteOfDay() {
        if (arrivalTime == null) {
            return Integer.MIN_VALUE;
        }
        return dayOffset * 1440 + arrivalTime.getHour() * 60 + arrivalTime.getMinute();
    }

    public int departureMinuteOfDay() {
        if (departureTime == null) {
            return Integer.MIN_VALUE;
        }
        return dayOffset * 1440 + departureTime.getHour() * 60 + departureTime.getMinute();
    }

    public boolean hasArrival() {
        return arrivalTime != null;
    }

    public boolean hasDeparture() {
        return departureTime != null;
    }
}
