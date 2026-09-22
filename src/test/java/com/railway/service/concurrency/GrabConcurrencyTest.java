package com.railway.service.concurrency;

import com.railway.common.ErrorCode;
import com.railway.dto.GrabResponse;
import com.railway.dto.OrderCreateRequest;
import com.railway.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrabConcurrencyTest {

    private static final String STOCK_KEY = GrabKeys.stockKey(
            "G1", LocalDate.of(2026, 4, 15), "二等座", "北京南", "上海虹桥");

    private GrabProperties properties;
    private InMemoryStockStore stockStore;
    private InMemoryDistributedLockManager lockManager;
    private InMemoryGrabOrderWriter orderWriter;
    private GrabProcessor processor;
    private GrabResultRepository repository;
    private GrabService grabService;
    private GrabQueue queue;

    @BeforeEach
    void setUp() {
        properties = new GrabProperties();
        properties.setQueueCapacity(200);
        properties.setWorkerCount(4);
        properties.setLockWaitMillis(5000);
        properties.setLockLeaseMillis(5000);
        stockStore = new InMemoryStockStore();
        lockManager = new InMemoryDistributedLockManager();
        orderWriter = new InMemoryGrabOrderWriter(0);
        processor = new GrabProcessor(lockManager, stockStore, orderWriter,
                properties.getLockWaitMillis(), properties.getLockLeaseMillis());
        repository = new GrabResultRepository();
        queue = new GrabQueue(processor, properties.getQueueCapacity(),
                properties.getWorkerCount(), repository::markFinished);
        grabService = new GrabService(queue, processor, stockStore, orderWriter, repository, properties);
    }

    @AfterEach
    void tearDown() {
        queue.shutdown();
    }

    @Test
    void syncGrabShouldCreateExactlyTenOrders() throws Exception {
        stockStore.warmUp(STOCK_KEY, 10);

        List<GrabResponse> responses = runConcurrent(100, true);

        long success = responses.stream().filter(r -> "SUCCESS".equals(r.getStatus())).count();
        assertEquals(10, success, "应恰好10人抢到票");
        assertEquals(90, responses.size() - success, "其余90人应失败");
        assertEquals(10, orderWriter.count(), "应恰好生成10个订单");
        assertEquals(0, stockStore.getRemaining(STOCK_KEY), "库存应归零且不为负");
    }

    @Test
    void asyncGrabShouldCreateExactlyTenOrders() throws Exception {
        stockStore.warmUp(STOCK_KEY, 10);

        List<GrabResponse> responses = runConcurrent(100, false);
        assertEquals(100, responses.size());
        assertTrue(responses.stream().allMatch(r -> "QUEUED".equals(r.getStatus())));

        boolean finished = awaitFinished(100, 30);
        assertTrue(finished, "异步下单应在超时前完成");

        assertEquals(10, repository.getSuccessCount(), "应恰好10个成功");
        assertEquals(90, repository.getFailedCount(), "应恰好90个余票不足");
        assertEquals(10, orderWriter.count(), "应恰好生成10个订单");
        assertEquals(0, stockStore.getRemaining(STOCK_KEY), "库存应归零且不为负");
        assertEquals(100, repository.getSubmittedCount());
        assertEquals(0, repository.getRejectedCount());
    }

    @Test
    void notWarmedUpStockShouldFailFast() {
        GrabResult result = processor.process(new GrabMessage("req-no-stock", request(1), STOCK_KEY,
                System.currentTimeMillis()));

        assertEquals(GrabResultStatus.FAILED, result.getStatus());
        assertEquals(ErrorCode.SEAT_SOLD_OUT.getCode(), result.getCode());
        assertEquals(0, orderWriter.count());
    }

    @Test
    void orderFailureShouldCompensateStock() {
        stockStore.warmUp(STOCK_KEY, 10);
        GrabOrderWriter failingWriter = new GrabOrderWriter() {
            @Override
            public com.railway.dto.OrderCreateVO create(OrderCreateRequest request) {
                throw new BusinessException(ErrorCode.ORDER_CREATE_FAILED, "模拟下单失败");
            }

            @Override
            public long count() {
                return 0;
            }

            @Override
            public boolean supportsReset() {
                return true;
            }

            @Override
            public void reset() {
            }
        };
        GrabProcessor failingProcessor = new GrabProcessor(lockManager, stockStore, failingWriter, 5000, 5000);

        GrabResult result = failingProcessor.process(new GrabMessage("req-fail", request(1), STOCK_KEY,
                System.currentTimeMillis()));

        assertEquals(GrabResultStatus.FAILED, result.getStatus());
        assertEquals(ErrorCode.ORDER_CREATE_FAILED.getCode(), result.getCode());
        assertEquals(10, stockStore.getRemaining(STOCK_KEY), "下单失败必须回补库存");
        assertEquals(0, orderWriter.count());
    }

    @Test
    void queueFullShouldBeRejected() {
        GrabProperties singleSlotProperties = new GrabProperties();
        GrabResultRepository singleRepository = new GrabResultRepository();
        GrabQueue singleSlotQueue = new GrabQueue(processor, 1, 0, singleRepository::markFinished);
        GrabService singleService = new GrabService(singleSlotQueue, processor, stockStore,
                orderWriter, singleRepository, singleSlotProperties);
        stockStore.warmUp(STOCK_KEY, 10);
        try {
            singleService.grab(request(1), false);
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> singleService.grab(request(2), false));
            assertEquals(ErrorCode.GRAB_QUEUE_FULL.getCode(), exception.getCode());
            assertEquals(1, singleRepository.getRejectedCount());
            assertEquals(1, singleSlotQueue.size());
        } finally {
            singleSlotQueue.shutdown();
        }
    }

    private List<GrabResponse> runConcurrent(int concurrency, boolean sync) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<GrabResponse>> futures = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                startGate.await();
                return grabService.grab(request(index), sync);
            }));
        }
        startGate.countDown();
        List<GrabResponse> responses = new ArrayList<>();
        for (Future<GrabResponse> future : futures) {
            responses.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return responses;
    }

    private boolean awaitFinished(int expected, int timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (repository.getSuccessCount() + repository.getFailedCount() >= expected) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private OrderCreateRequest request(int index) {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setUserId(9000L + index);
        request.setTrainNo("G1");
        request.setTravelDate(LocalDate.of(2026, 4, 15));
        request.setFromStation("北京南");
        request.setToStation("上海虹桥");
        request.setSeatType("二等座");
        request.setPassengerName("抢票旅客" + index);
        return request;
    }
}
