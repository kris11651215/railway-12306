package com.railway.controller;

import com.railway.common.ApiResponse;
import com.railway.dto.OrderCreateRequest;
import com.railway.dto.OrderCreateVO;
import com.railway.service.OrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<OrderCreateVO> createOrder(@RequestBody OrderCreateRequest request) {
        return ApiResponse.ok(orderService.createOrder(request));
    }
}
