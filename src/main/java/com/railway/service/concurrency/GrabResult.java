package com.railway.service.concurrency;

import com.railway.dto.OrderCreateVO;

import java.math.BigDecimal;

public class GrabResult {

    private final String requestId;
    private final long submittedAtMillis;
    private volatile GrabResultStatus status;
    private volatile int queuePosition;
    private volatile int code;
    private volatile String message;
    private volatile String orderNo;
    private volatile BigDecimal price;
    private volatile long finishedAtMillis;

    private GrabResult(String requestId, long submittedAtMillis) {
        this.requestId = requestId;
        this.submittedAtMillis = submittedAtMillis;
    }

    public static GrabResult queued(String requestId, int queuePosition, long submittedAtMillis) {
        GrabResult result = new GrabResult(requestId, submittedAtMillis);
        result.status = GrabResultStatus.QUEUED;
        result.queuePosition = queuePosition;
        result.message = "已进入抢票队列，排队位置约 " + queuePosition;
        return result;
    }

    public static GrabResult success(String requestId, long submittedAtMillis, OrderCreateVO order) {
        GrabResult result = new GrabResult(requestId, submittedAtMillis);
        result.status = GrabResultStatus.SUCCESS;
        result.code = 0;
        result.message = "抢票成功";
        result.orderNo = order.getOrderNo();
        result.price = order.getPrice();
        result.finishedAtMillis = System.currentTimeMillis();
        return result;
    }

    public static GrabResult failed(String requestId, long submittedAtMillis, int code, String message) {
        GrabResult result = new GrabResult(requestId, submittedAtMillis);
        result.status = GrabResultStatus.FAILED;
        result.code = code;
        result.message = message;
        result.finishedAtMillis = System.currentTimeMillis();
        return result;
    }

    public String getRequestId() {
        return requestId;
    }

    public long getSubmittedAtMillis() {
        return submittedAtMillis;
    }

    public GrabResultStatus getStatus() {
        return status;
    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getFinishedAtMillis() {
        return finishedAtMillis;
    }

    public long getCostMillis() {
        return finishedAtMillis <= 0 ? 0 : finishedAtMillis - submittedAtMillis;
    }
}
