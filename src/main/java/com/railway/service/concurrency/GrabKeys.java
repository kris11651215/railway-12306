package com.railway.service.concurrency;

import com.railway.dto.OrderCreateRequest;

import java.time.LocalDate;

public final class GrabKeys {

    public static final String DEFAULT_SEAT_TYPE = "二等座";

    private GrabKeys() {
    }

    public static String lockKey(OrderCreateRequest request) {
        return lockKey(request.getTrainNo(), request.getTravelDate(), seatType(request));
    }

    public static String lockKey(String trainNo, LocalDate travelDate, String seatType) {
        return "railway:lock:grab:" + trainNo + ":" + travelDate + ":" + seatType;
    }

    public static String stockKey(OrderCreateRequest request) {
        return stockKey(request.getTrainNo(), request.getTravelDate(), seatType(request),
                request.getFromStation(), request.getToStation());
    }

    public static String stockKey(String trainNo, LocalDate travelDate, String seatType,
                                  String fromStation, String toStation) {
        return "railway:stock:" + trainNo + ":" + travelDate + ":" + seatType
                + ":" + fromStation + "->" + toStation;
    }

    public static String seatType(OrderCreateRequest request) {
        String seatType = request.getSeatType();
        return (seatType == null || seatType.isBlank()) ? DEFAULT_SEAT_TYPE : seatType;
    }
}
