package com.railway.service.concurrency;

import com.railway.dto.OrderCreateRequest;
import com.railway.dto.OrderCreateVO;
import com.railway.entity.TicketOrder;
import com.railway.entity.TicketStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryGrabOrderWriter implements GrabOrderWriter {

    private static final DateTimeFormatter ORDER_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final Map<String, TicketOrder> orders = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1000);
    private final AtomicLong orderSequence = new AtomicLong(1);
    private final long simulateCostMillis;

    public InMemoryGrabOrderWriter(long simulateCostMillis) {
        this.simulateCostMillis = simulateCostMillis;
    }

    @Override
    public OrderCreateVO create(OrderCreateRequest request) {
        if (simulateCostMillis > 0) {
            try {
                Thread.sleep(simulateCostMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("模拟下单被中断");
            }
        }
        TicketOrder order = new TicketOrder();
        order.setId(idSequence.incrementAndGet());
        order.setOrderNo("GRAB" + LocalDateTime.now().format(ORDER_NO_FORMAT)
                + String.format("%04d", orderSequence.getAndIncrement()));
        order.setUserId(request.getUserId());
        order.setTravelDate(request.getTravelDate());
        order.setSeatType(GrabKeys.seatType(request));
        order.setPassengerName(request.getPassengerName());
        order.setPassengerIdCard(request.getPassengerIdCard());
        order.setPrice(BigDecimal.ZERO);
        order.setStatus(TicketStatus.PENDING_PAYMENT);
        order.setCreatedAt(LocalDateTime.now());
        order.setExpireAt(LocalDateTime.now().plusMinutes(15));
        orders.put(order.getOrderNo(), order);

        OrderCreateVO vo = new OrderCreateVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTrainNo(request.getTrainNo());
        vo.setFromStation(request.getFromStation());
        vo.setToStation(request.getToStation());
        vo.setSeatType(order.getSeatType());
        vo.setPrice(order.getPrice());
        vo.setStatus(order.getStatus().getChineseName());
        vo.setExpireAt(order.getExpireAt());
        return vo;
    }

    @Override
    public long count() {
        return orders.size();
    }

    public List<TicketOrder> findAll() {
        return new ArrayList<>(orders.values());
    }

    @Override
    public boolean supportsReset() {
        return true;
    }

    @Override
    public void reset() {
        orders.clear();
    }
}
