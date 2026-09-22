package com.railway.dto;

import java.math.BigDecimal;

public class SegmentPriceRow {

    private String trainNo;
    private Integer fromStationOrder;
    private BigDecimal price;

    public String getTrainNo() {
        return trainNo;
    }

    public void setTrainNo(String trainNo) {
        this.trainNo = trainNo;
    }

    public Integer getFromStationOrder() {
        return fromStationOrder;
    }

    public void setFromStationOrder(Integer fromStationOrder) {
        this.fromStationOrder = fromStationOrder;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
