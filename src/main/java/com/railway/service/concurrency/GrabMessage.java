package com.railway.service.concurrency;

import com.railway.dto.OrderCreateRequest;

public class GrabMessage {

    private final String requestId;
    private final OrderCreateRequest request;
    private final String stockKey;
    private final long submittedAtMillis;

    public GrabMessage(String requestId, OrderCreateRequest request, String stockKey, long submittedAtMillis) {
        this.requestId = requestId;
        this.request = request;
        this.stockKey = stockKey;
        this.submittedAtMillis = submittedAtMillis;
    }

    public String getRequestId() {
        return requestId;
    }

    public OrderCreateRequest getRequest() {
        return request;
    }

    public String getStockKey() {
        return stockKey;
    }

    public long getSubmittedAtMillis() {
        return submittedAtMillis;
    }
}
