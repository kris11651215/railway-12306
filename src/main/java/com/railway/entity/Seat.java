package com.railway.entity;

import java.math.BigDecimal;
import java.util.Objects;

public class Seat extends RailwayEntity {

    private String trainNo;
    private SeatType seatType;
    private int totalCount;
    private int remainingCount;
    private BigDecimal price;

    public Seat() {
    }

    public Seat(Long id, String trainNo, SeatType seatType, int totalCount, int remainingCount, BigDecimal price) {
        super(id);
        this.trainNo = trainNo;
        this.seatType = seatType;
        this.totalCount = totalCount;
        this.remainingCount = remainingCount;
        this.price = price;
    }

    public String getTrainNo() {
        return trainNo;
    }

    public void setTrainNo(String trainNo) {
        this.trainNo = trainNo;
    }

    public SeatType getSeatType() {
        return seatType;
    }

    public void setSeatType(SeatType seatType) {
        this.seatType = seatType;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getRemainingCount() {
        return remainingCount;
    }

    public void setRemainingCount(int remainingCount) {
        this.remainingCount = remainingCount;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Override
    public RailwaySystem getSystemCategory() {
        return RailwaySystem.ROLLING_STOCK;
    }

    @Override
    public String toString() {
        return String.format("Seat{train=%s, type=%s, total=%d, remaining=%d, price=%s}",
                trainNo, seatType == null ? null : seatType.getChineseName(),
                totalCount, remainingCount, price);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Seat)) {
            return false;
        }
        Seat seat = (Seat) o;
        return Objects.equals(trainNo, seat.trainNo) && seatType == seat.seatType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(trainNo, seatType);
    }
}
