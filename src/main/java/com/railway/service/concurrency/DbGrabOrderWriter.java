package com.railway.service.concurrency;

import com.railway.dto.OrderCreateRequest;
import com.railway.dto.OrderCreateVO;
import com.railway.service.OrderService;

import java.util.concurrent.atomic.AtomicLong;

public class DbGrabOrderWriter implements GrabOrderWriter {

    private final OrderService orderService;
    private final AtomicLong createdCount = new AtomicLong();

    public DbGrabOrderWriter(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public OrderCreateVO create(OrderCreateRequest request) {
        OrderCreateVO order = orderService.createOrder(request);
        createdCount.incrementAndGet();
        return order;
    }

    @Override
    public long count() {
        return createdCount.get();
    }

    @Override
    public boolean supportsReset() {
        return false;
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException("数据库订单模式不支持重置");
    }
}
