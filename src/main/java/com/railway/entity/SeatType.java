package com.railway.entity;

public enum SeatType {

    BUSINESS("商务座"),
    FIRST_CLASS("一等座"),
    SECOND_CLASS("二等座"),
    SOFT_SLEEPER("软卧"),
    HARD_SLEEPER("硬卧"),
    HARD_SEAT("硬座"),
    STANDING("无座");

    private final String chineseName;

    SeatType(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }
}
