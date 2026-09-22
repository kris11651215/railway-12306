package com.railway.service.concurrency;

import com.railway.common.ErrorCode;
import com.railway.dto.GrabResponse;
import com.railway.dto.GrabResultVO;
import com.railway.dto.GrabStatsVO;
import com.railway.dto.OrderCreateRequest;
import com.railway.dto.StockViewVO;
import com.railway.dto.StockWarmupRequest;
import com.railway.exception.BusinessException;
import com.railway.service.OrderRequestValidator;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class GrabService {

    private final GrabQueue grabQueue;
    private final GrabProcessor grabProcessor;
    private final StockStore stockStore;
    private final GrabOrderWriter orderWriter;
    private final GrabResultRepository resultRepository;
    private final GrabProperties properties;

    public GrabService(GrabQueue grabQueue, GrabProcessor grabProcessor, StockStore stockStore,
                       GrabOrderWriter orderWriter, GrabResultRepository resultRepository,
                       GrabProperties properties) {
        this.grabQueue = grabQueue;
        this.grabProcessor = grabProcessor;
        this.stockStore = stockStore;
        this.orderWriter = orderWriter;
        this.resultRepository = resultRepository;
        this.properties = properties;
    }

    public GrabResponse grab(OrderCreateRequest request, boolean sync) {
        OrderRequestValidator.validate(request);
        String requestId = UUID.randomUUID().toString().replace("-", "");
        long submittedAt = System.currentTimeMillis();
        String stockKey = GrabKeys.stockKey(request);

        if (sync) {
            resultRepository.markSubmitted(GrabResult.queued(requestId, 0, submittedAt));
            GrabResult result = grabProcessor.process(
                    new GrabMessage(requestId, request, stockKey, submittedAt));
            resultRepository.markFinished(result);
            return toResponse(result, "sync");
        }

        GrabMessage message = new GrabMessage(requestId, request, stockKey, submittedAt);
        GrabResult queued = GrabResult.queued(requestId, grabQueue.size() + 1, submittedAt);
        resultRepository.markSubmitted(queued);
        if (!grabQueue.submit(message)) {
            resultRepository.markFinished(GrabResult.failed(requestId, submittedAt,
                    ErrorCode.GRAB_QUEUE_FULL.getCode(), "抢票队列已满，请稍后重试"));
            throw new BusinessException(ErrorCode.GRAB_QUEUE_FULL, "当前抢票人数过多，请稍后重试");
        }
        return toResponse(queued, "async");
    }

    public GrabResultVO getResult(String requestId) {
        GrabResult result = resultRepository.get(requestId);
        if (result == null) {
            throw new BusinessException(ErrorCode.GRAB_NOT_FOUND, "抢票请求不存在：" + requestId);
        }
        return toResultVO(result);
    }

    public GrabStatsVO stats() {
        GrabStatsVO vo = new GrabStatsVO();
        vo.setSubmitted(resultRepository.getSubmittedCount());
        vo.setSuccess(resultRepository.getSuccessCount());
        vo.setFailed(resultRepository.getFailedCount());
        vo.setRejected(resultRepository.getRejectedCount());
        vo.setOrders(orderWriter.count());
        vo.setQueueSize(grabQueue.size());
        vo.setQueueCapacity(grabQueue.getCapacity());
        vo.setWorkerCount(grabQueue.getWorkerCount());
        vo.setFirstSubmittedMillis(resultRepository.getFirstSubmittedMillis());
        vo.setLastFinishedMillis(resultRepository.getLastFinishedMillis());
        long first = resultRepository.getFirstSubmittedMillis();
        long last = resultRepository.getLastFinishedMillis();
        long elapsed = (first > 0 && last >= first) ? last - first : 0L;
        vo.setElapsedMillis(elapsed);
        if (elapsed > 0) {
            double qps = resultRepository.getSubmittedCount() * 1000.0 / elapsed;
            vo.setQps(BigDecimal.valueOf(qps).setScale(2, RoundingMode.HALF_UP).doubleValue());
        }
        return vo;
    }

    public StockViewVO warmUp(StockWarmupRequest request) {
        if (request == null || request.getTrainNo() == null || request.getTrainNo().isBlank()
                || request.getTravelDate() == null
                || request.getFromStation() == null || request.getFromStation().isBlank()
                || request.getToStation() == null || request.getToStation().isBlank()
                || request.getTotalCount() == null || request.getTotalCount() < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "trainNo/travelDate/fromStation/toStation/totalCount 均为必填且 totalCount>=1");
        }
        String stockKey = GrabKeys.stockKey(request.getTrainNo(), request.getTravelDate(),
                resolveSeatType(request.getSeatType()), request.getFromStation(), request.getToStation());
        stockStore.warmUp(stockKey, request.getTotalCount());
        return toStockView(stockKey);
    }

    public StockViewVO stockView(String trainNo, LocalDate travelDate, String seatType,
                                 String fromStation, String toStation) {
        if (trainNo == null || trainNo.isBlank() || travelDate == null
                || fromStation == null || fromStation.isBlank()
                || toStation == null || toStation.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "trainNo/travelDate/fromStation/toStation 均为必填");
        }
        String stockKey = GrabKeys.stockKey(trainNo, travelDate, resolveSeatType(seatType),
                fromStation, toStation);
        return toStockView(stockKey);
    }

    public Map<String, Object> reset() {
        resultRepository.reset();
        stockStore.clear();
        if (orderWriter.supportsReset()) {
            orderWriter.reset();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resultCleared", true);
        data.put("stockCleared", true);
        data.put("orderStoreReset", orderWriter.supportsReset());
        return data;
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanupExpiredResults() {
        resultRepository.cleanup(properties.getRequestTtlMillis());
    }

    private StockViewVO toStockView(String stockKey) {
        StockViewVO vo = new StockViewVO();
        vo.setStockKey(stockKey);
        vo.setRemaining(stockStore.getRemaining(stockKey));
        vo.setInitialized(stockStore.contains(stockKey));
        return vo;
    }

    private GrabResponse toResponse(GrabResult result, String mode) {
        GrabResponse response = new GrabResponse();
        response.setRequestId(result.getRequestId());
        response.setStatus(result.getStatus().name());
        response.setMode(mode);
        response.setQueuePosition(result.getQueuePosition());
        response.setOrderNo(result.getOrderNo());
        response.setPrice(result.getPrice());
        response.setMessage(result.getMessage());
        return response;
    }

    private GrabResultVO toResultVO(GrabResult result) {
        GrabResultVO vo = new GrabResultVO();
        vo.setRequestId(result.getRequestId());
        vo.setStatus(result.getStatus().name());
        vo.setCode(result.getCode());
        vo.setMessage(result.getMessage());
        vo.setOrderNo(result.getOrderNo());
        vo.setPrice(result.getPrice());
        vo.setQueuePosition(result.getQueuePosition());
        vo.setSubmittedAtMillis(result.getSubmittedAtMillis());
        vo.setFinishedAtMillis(result.getFinishedAtMillis());
        vo.setCostMillis(result.getCostMillis());
        return vo;
    }

    private String resolveSeatType(String seatType) {
        return (seatType == null || seatType.isBlank()) ? GrabKeys.DEFAULT_SEAT_TYPE : seatType;
    }
}
