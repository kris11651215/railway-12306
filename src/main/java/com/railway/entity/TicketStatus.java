package com.railway.entity;

public enum TicketStatus {

    PENDING_PAYMENT("待支付"),
    PAID("已支付"),
    REFUNDED("已退票"),
    CANCELLED("已取消");

    private final String chineseName;

    TicketStatus(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }
}
