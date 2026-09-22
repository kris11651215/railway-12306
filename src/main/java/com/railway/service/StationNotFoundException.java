package com.railway.service;

public class StationNotFoundException extends RuntimeException {

    public StationNotFoundException(String stationName) {
        super("未找到车站：" + stationName);
    }
}
