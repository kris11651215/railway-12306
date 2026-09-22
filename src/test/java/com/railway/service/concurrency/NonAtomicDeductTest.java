package com.railway.service.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NonAtomicDeductTest {

    @Test
    void checkThenActWithoutLockShouldLoseUpdates() throws Exception {
        int threads = 100;
        AtomicInteger stock = new AtomicInteger(10);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    int current = stock.get();
                    barrier.await(10, TimeUnit.SECONDS);
                    if (current >= 1) {
                        success.incrementAndGet();
                        stock.set(current - 1);
                    } else {
                        soldOut.incrementAndGet();
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(100, success.get(), "无锁 check-then-act 下100人全部以为自己抢到");
        assertEquals(0, soldOut.get());
        assertEquals(9, stock.get(), "10张票只卖出1张的库存效果，丢了99次更新");
    }

    @Test
    void casLoopShouldNotLoseUpdates() throws Exception {
        int threads = 100;
        InMemoryStockStore stockStore = new InMemoryStockStore();
        stockStore.warmUp("stock", 10);
        AtomicInteger success = new AtomicInteger();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    if (stockStore.deduct("stock", 1) == StockDeductResult.OK) {
                        success.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        startGate.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(10, success.get(), "CAS 原子扣减应恰好成功10次");
        assertEquals(0, stockStore.getRemaining("stock"));
    }
}
