package com.railway.service.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

public class InMemoryDistributedLockManager implements DistributedLockManager {

    private static final Logger log = LoggerFactory.getLogger(InMemoryDistributedLockManager.class);
    private static final long RETRY_INTERVAL_NANOS = 20_000_000L;

    private final Map<String, LockEntry> locks = new ConcurrentHashMap<>();

    @Override
    public DistributedLock getLock(String key) {
        return new InMemoryDistributedLock(key);
    }

    private boolean tryAcquire(String key, long waitMillis, long leaseMillis) {
        long deadline = System.currentTimeMillis() + Math.max(waitMillis, 0);
        do {
            LockEntry entry = locks.computeIfAbsent(key, k -> new LockEntry());
            synchronized (entry) {
                long now = System.currentTimeMillis();
                Thread current = Thread.currentThread();
                if (entry.owner == null || entry.expireAt <= now) {
                    if (entry.owner != null) {
                        log.warn("内存锁已过期释放: key={}, oldOwner={}", key, entry.owner.getName());
                    }
                    entry.owner = current;
                    entry.holdCount = 1;
                    entry.expireAt = now + leaseMillis;
                    return true;
                }
                if (entry.owner == current) {
                    entry.holdCount++;
                    entry.expireAt = now + leaseMillis;
                    return true;
                }
            }
            LockSupport.parkNanos(RETRY_INTERVAL_NANOS);
        } while (System.currentTimeMillis() < deadline);
        return false;
    }

    private void release(String key) {
        LockEntry entry = locks.get(key);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (entry.owner != Thread.currentThread()) {
                log.warn("内存锁不属于当前线程，忽略解锁: key={}", key);
                return;
            }
            entry.holdCount--;
            if (entry.holdCount <= 0) {
                entry.owner = null;
                entry.holdCount = 0;
            }
        }
    }

    private static final class LockEntry {
        private Thread owner;
        private int holdCount;
        private long expireAt;
    }

    private final class InMemoryDistributedLock implements DistributedLock {

        private final String key;

        private InMemoryDistributedLock(String key) {
            this.key = key;
        }

        @Override
        public boolean tryLock(long waitMillis, long leaseMillis) {
            return tryAcquire(key, waitMillis, leaseMillis);
        }

        @Override
        public void unlock() {
            release(key);
        }

        @Override
        public String getKey() {
            return key;
        }
    }
}
