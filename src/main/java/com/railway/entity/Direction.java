package com.railway.entity;

public enum Direction {

    UP("上行", true),
    DOWN("下行", false);

    private final String chineseName;
    private final boolean toBeijing;

    Direction(String chineseName, boolean toBeijing) {
        this.chineseName = chineseName;
        this.toBeijing = toBeijing;
    }

    public String getChineseName() {
        return chineseName;
    }

    public boolean isToBeijing() {
        return toBeijing;
    }
}
