package com.railway.service;

import com.railway.common.ErrorCode;
import com.railway.dto.OrderCreateRequest;
import com.railway.exception.BusinessException;

public final class OrderRequestValidator {

    private OrderRequestValidator() {
    }

    public static void validate(OrderCreateRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId 不能为空");
        }
        if (request.getTrainNo() == null || request.getTrainNo().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "trainNo 不能为空");
        }
        if (request.getTravelDate() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "travelDate 不能为空");
        }
        if (request.getFromStation() == null || request.getFromStation().isBlank()
                || request.getToStation() == null || request.getToStation().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "出发站与到达站不能为空");
        }
        if (request.getPassengerName() == null || request.getPassengerName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "passengerName 不能为空");
        }
    }
}
