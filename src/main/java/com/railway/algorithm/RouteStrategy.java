package com.railway.algorithm;

public enum RouteStrategy {

    FASTEST("fastest", "最快"),
    CHEAPEST("cheapest", "最经济");

    private final String code;
    private final String chineseName;

    RouteStrategy(String code, String chineseName) {
        this.code = code;
        this.chineseName = chineseName;
    }

    public String getCode() {
        return code;
    }

    public String getChineseName() {
        return chineseName;
    }

    public static RouteStrategy fromCode(String code) {
        if (code == null || code.isBlank()) {
            return FASTEST;
        }
        for (RouteStrategy strategy : values()) {
            if (strategy.code.equalsIgnoreCase(code.trim())) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("不支持的策略：" + code + "，可选 fastest 或 cheapest");
    }
}
