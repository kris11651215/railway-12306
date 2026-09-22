package com.railway.common;

public enum ErrorCode {

    SUCCESS(0, "成功"),
    PARAM_ERROR(400, "参数错误"),
    STATION_NOT_FOUND(1001, "车站不存在"),
    TRAIN_NOT_FOUND(1002, "车次不存在或不经过该区间"),
    SEAT_SOLD_OUT(2001, "余票不足"),
    ORDER_CREATE_FAILED(2002, "订单创建失败"),
    GRAB_QUEUE_FULL(2003, "抢票队列已满，请稍后重试"),
    GRAB_NOT_FOUND(2004, "抢票请求不存在"),
    ROUTE_NOT_FOUND(3001, "无可行换乘方案"),
    INTERNAL_ERROR(500, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
