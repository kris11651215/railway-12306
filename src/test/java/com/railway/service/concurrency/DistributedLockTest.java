package com.railway.service.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedLockTest {

    @Test
    void lockShouldSerializeIncrement() throws Exception {
        InMemoryDistributedLockManager manager = new InMemoryDistributedLockManager();
        int threads = 20;
        int loops = 500;
        AtomicInteger counter = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < loops; j++) {
                        DistributedLock lock = manager.getLock("counter");
                        if (lock.tryLock(5000, 5000)) {
                            try {
                                counter.incrementAndGet();
                            } finally {
                                lock.unlock();
                            }
                        }
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(threads * loops, counter.get());
    }

    @Test
    void sameThreadShouldAcquireReentrantly() {
        InMemoryDistributedLockManager manager = new InMemoryDistributedLockManager();
        DistributedLock lock = manager.getLock("reentrant");
        assertTrue(lock.tryLock(1000, 1000));
        assertTrue(lock.tryLock(1000, 1000));
        lock.unlock();
        lock.unlock();
        assertTrue(manager.getLock("reentrant").tryLock(100, 1000));
    }

    @Test
    void expiredLockShouldBePreempted() throws Exception {
        InMemoryDistributedLockManager manager = new InMemoryDistributedLockManager();
        DistributedLock first = manager.getLock("expire");
        assertTrue(first.tryLock(0, 50));
        Thread.sleep(150);
        AtomicBoolean acquired = new AtomicBoolean(false);
        Thread other = new Thread(() -> acquired.set(manager.getLock("expire").tryLock(1000, 1000)));
        other.start();
        other.join(3000);
        assertTrue(acquired.get());
    }

    @Test
    void waitTimeoutShouldReturnFalseWhileHeld() throws Exception {
        InMemoryDistributedLockManager manager = new InMemoryDistributedLockManager();
        DistributedLock holder = manager.getLock("busy");
        assertTrue(holder.tryLock(0, 5000));
        CountDownLatch released = new CountDownLatch(1);
        Thread other = new Thread(() -> {
            boolean locked = manager.getLock("busy").tryLock(100, 1000);
            assertFalse(locked);
            released.countDown();
        });
        other.start();
        assertTrue(released.await(3, TimeUnit.SECONDS));
        holder.unlock();
    }
}
