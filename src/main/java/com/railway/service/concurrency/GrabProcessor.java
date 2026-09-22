package com.railway.service.concurrency;

import com.railway.common.ErrorCode;
import com.railway.dto.OrderCreateVO;
import com.railway.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrabProcessor {

    private static final Logger log = LoggerFactory.getLogger(GrabProcessor.class);

    private final DistributedLockManager lockManager;
    private final StockStore stockStore;
    private final GrabOrderWriter orderWriter;
    private final long lockWaitMillis;
    private final long lockLeaseMillis;

    public GrabProcessor(DistributedLockManager lockManager, StockStore stockStore,
                         GrabOrderWriter orderWriter, long lockWaitMillis, long lockLeaseMillis) {
        this.lockManager = lockManager;
        this.stockStore = stockStore;
        this.orderWriter = orderWriter;
        this.lockWaitMillis = lockWaitMillis;
        this.lockLeaseMillis = lockLeaseMillis;
    }

    public GrabResult process(GrabMessage message) {
        String requestId = message.getRequestId();
        long submittedAt = message.getSubmittedAtMillis();
        DistributedLock lock = lockManager.getLock(GrabKeys.lockKey(message.getRequest()));
        boolean locked = false;
        try {
            locked = lock.tryLock(lockWaitMillis, lockLeaseMillis);
            if (!locked) {
                return GrabResult.failed(requestId, submittedAt,
                        ErrorCode.ORDER_CREATE_FAILED.getCode(), "获取分布式锁超时，请重试");
            }

            StockDeductResult deductResult = stockStore.deduct(message.getStockKey(), 1);
            if (deductResult == StockDeductResult.NOT_INITIALIZED) {
                return GrabResult.failed(requestId, submittedAt,
                        ErrorCode.SEAT_SOLD_OUT.getCode(), "库存尚未预热，请联系管理员");
            }
            if (deductResult == StockDeductResult.NO_STOCK) {
                return GrabResult.failed(requestId, submittedAt,
                        ErrorCode.SEAT_SOLD_OUT.getCode(), "余票不足，抢票失败");
            }

            try {
                OrderCreateVO order = orderWriter.create(message.getRequest());
                return GrabResult.success(requestId, submittedAt, order);
            } catch (Exception e) {
                stockStore.compensate(message.getStockKey(), 1);
                int code = e instanceof BusinessException businessException
                        ? businessException.getCode() : ErrorCode.ORDER_CREATE_FAILED.getCode();
                log.warn("下单失败已回补库存: requestId={}, reason={}", requestId, e.getMessage());
                return GrabResult.failed(requestId, submittedAt, code, e.getMessage());
            }
        } catch (Exception e) {
            log.error("抢票处理异常: requestId={}", requestId, e);
            return GrabResult.failed(requestId, submittedAt,
                    ErrorCode.INTERNAL_ERROR.getCode(), "系统繁忙，请稍后重试");
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }
}
