package com.railway.dto;

import java.math.BigDecimal;
import java.util.List;

public class TransferPlanVO {

    private String fromStation;
    private String toStation;
    private String strategy;
    private String strategyName;
    private String algorithm;
    private int transferCount;
    private List<String> transferStations;
    private int totalDurationMinutes;
    private BigDecimal totalPrice;
    private List<Leg> legs;

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

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public int getTransferCount() {
        return transferCount;
    }

    public void setTransferCount(int transferCount) {
        this.transferCount = transferCount;
    }

    public List<String> getTransferStations() {
        return transferStations;
    }

    public void setTransferStations(List<String> transferStations) {
        this.transferStations = transferStations;
    }

    public int getTotalDurationMinutes() {
        return totalDurationMinutes;
    }

    public void setTotalDurationMinutes(int totalDurationMinutes) {
        this.totalDurationMinutes = totalDurationMinutes;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public List<Leg> getLegs() {
        return legs;
    }

    public void setLegs(List<Leg> legs) {
        this.legs = legs;
    }

    public static class Leg {

        private String trainNo;
        private String trainType;
        private String fromStation;
        private String toStation;
        private String departureTime;
        private String arrivalTime;
        private int departureDayOffset;
        private int arrivalDayOffset;
        private int durationMinutes;
        private BigDecimal price;
        private int waitBeforeMinutes;

        public String getTrainNo() {
            return trainNo;
        }

        public void setTrainNo(String trainNo) {
            this.trainNo = trainNo;
        }

        public String getTrainType() {
            return trainType;
        }

        public void setTrainType(String trainType) {
            this.trainType = trainType;
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

        public String getDepartureTime() {
            return departureTime;
        }

        public void setDepartureTime(String departureTime) {
            this.departureTime = departureTime;
        }

        public String getArrivalTime() {
            return arrivalTime;
        }

        public void setArrivalTime(String arrivalTime) {
            this.arrivalTime = arrivalTime;
        }

        public int getDepartureDayOffset() {
            return departureDayOffset;
        }

        public void setDepartureDayOffset(int departureDayOffset) {
            this.departureDayOffset = departureDayOffset;
        }

        public int getArrivalDayOffset() {
            return arrivalDayOffset;
        }

        public void setArrivalDayOffset(int arrivalDayOffset) {
            this.arrivalDayOffset = arrivalDayOffset;
        }

        public int getDurationMinutes() {
            return durationMinutes;
        }

        public void setDurationMinutes(int durationMinutes) {
            this.durationMinutes = durationMinutes;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public int getWaitBeforeMinutes() {
            return waitBeforeMinutes;
        }

        public void setWaitBeforeMinutes(int waitBeforeMinutes) {
            this.waitBeforeMinutes = waitBeforeMinutes;
        }
    }
}
