package com.railway.dto;

import java.math.BigDecimal;

public class GrabResultVO {

    private String requestId;
    private String status;
    private int code;
    private String message;
    private String orderNo;
    private BigDecimal price;
    private int queuePosition;
    private long submittedAtMillis;
    private long finishedAtMillis;
    private long costMillis;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(int queuePosition) {
        this.queuePosition = queuePosition;
    }

    public long getSubmittedAtMillis() {
        return submittedAtMillis;
    }

    public void setSubmittedAtMillis(long submittedAtMillis) {
        this.submittedAtMillis = submittedAtMillis;
    }

    public long getFinishedAtMillis() {
        return finishedAtMillis;
    }

    public void setFinishedAtMillis(long finishedAtMillis) {
        this.finishedAtMillis = finishedAtMillis;
    }

    public long getCostMillis() {
        return costMillis;
    }

    public void setCostMillis(long costMillis) {
        this.costMillis = costMillis;
    }
}
