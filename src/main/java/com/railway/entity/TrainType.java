package com.railway.entity;

public enum TrainType {

    G("高速动车组", 1, 350),
    D("动车组", 2, 250),
    C("城际动车组", 3, 250),
    Z("直达特快", 4, 160),
    T("特快", 5, 140),
    K("快速", 6, 120),
    P("普客", 7, 100);

    private final String chineseName;
    private final int level;
    private final int maxSpeed;

    TrainType(String chineseName, int level, int maxSpeed) {
        this.chineseName = chineseName;
        this.level = level;
        this.maxSpeed = maxSpeed;
    }

    public String getChineseName() {
        return chineseName;
    }

    public int getLevel() {
        return level;
    }

    public int getMaxSpeed() {
        return maxSpeed;
    }

    public static TrainType fromTrainNo(String trainNo) {
        if (trainNo == null || trainNo.isEmpty()) {
            throw new IllegalArgumentException("车次号不能为空");
        }
        char prefix = Character.toUpperCase(trainNo.charAt(0));
        for (TrainType type : values()) {
            if (type.name().charAt(0) == prefix) {
                return type;
            }
        }
        return P;
    }
}
