package com.railway.ruleengine;

public class StationRouteQuery {

    private final String stationName;
    private final String trainNo;
    private final int formationLength;
    private final String trackNo;
    private final String entryDirection;
    private final String passengerFloor;

    public StationRouteQuery(String stationName, String trainNo, int formationLength,
                             String trackNo, String entryDirection, String passengerFloor) {
        this.stationName = stationName;
        this.trainNo = trainNo;
        this.formationLength = formationLength;
        this.trackNo = trackNo;
        this.entryDirection = entryDirection;
        this.passengerFloor = passengerFloor;
    }

    public String getStationName() {
        return stationName;
    }

    public String getTrainNo() {
        return trainNo;
    }

    public int getFormationLength() {
        return formationLength;
    }

    public String getTrackNo() {
        return trackNo;
    }

    public String getEntryDirection() {
        return entryDirection;
    }

    public String getPassengerFloor() {
        return passengerFloor;
    }
}
