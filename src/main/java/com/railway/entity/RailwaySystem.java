package com.railway.entity;

public enum RailwaySystem {

    CAR_SERVICE("车务", "车站组织与客运服务"),
    LOCOMOTIVE("机务", "机车运用与检修"),
    TRACK("工务", "线路桥隧养护"),
    SIGNAL("电务", "信号与通信设备"),
    ROLLING_STOCK("车辆", "客货车辆检修运用");

    private final String chineseName;
    private final String description;

    RailwaySystem(String chineseName, String description) {
        this.chineseName = chineseName;
        this.description = description;
    }

    public String getChineseName() {
        return chineseName;
    }

    public String getDescription() {
        return description;
    }
}
