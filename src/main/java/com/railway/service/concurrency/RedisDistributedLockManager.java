package com.railway.service.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class RedisDistributedLockManager implements DistributedLockManager {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLockManager.class);
    private static final long RETRY_INTERVAL_NANOS = 50_000_000L;

    private static final RedisScript<Long> ACQUIRE_SCRIPT = script("""
            if redis.call('exists', KEYS[1]) == 0 then
                redis.call('hincrby', KEYS[1], ARGV[2], 1)
                redis.call('pexpire', KEYS[1], ARGV[1])
                return 1
            end
            if redis.call('hexists', KEYS[1], ARGV[2]) == 1 then
                redis.call('hincrby', KEYS[1], ARGV[2], 1)
                redis.call('pexpire', KEYS[1], ARGV[1])
                return 1
            end
            return 0
            """);

    private static final RedisScript<Long> RENEW_SCRIPT = script("""
            if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then
                return redis.call('pexpire', KEYS[1], ARGV[2])
            end
            return 0
            """);

    private static final RedisScript<Long> RELEASE_SCRIPT = script("""
            if redis.call('hexists', KEYS[1], ARGV[2]) == 0 then
                return -1
            end
            local holdCount = redis.call('hincrby', KEYS[1], ARGV[2], -1)
            if holdCount > 0 then
                redis.call('pexpire', KEYS[1], ARGV[1])
                return holdCount
            end
            redis.call('del', KEYS[1])
            return 0
            """);

    private final StringRedisTemplate redisTemplate;
    private final String clientId = UUID.randomUUID().toString();
    private final ScheduledExecutorService renewExecutor = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "redis-lock-renew");
        thread.setDaemon(true);
        return thread;
    });

    public RedisDistributedLockManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public DistributedLock getLock(String key) {
        return new RedisDistributedLock(key);
    }

    private static RedisScript<Long> script(String text) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(text, Long.class);
        return redisScript;
    }

    private final class RedisDistributedLock implements DistributedLock {

        private final String key;
        private volatile String acquiredOwner;
        private volatile long leaseMillis = 30_000L;
        private volatile ScheduledFuture<?> renewTask;

        private RedisDistributedLock(String key) {
            this.key = key;
        }

        @Override
        public boolean tryLock(long waitMillis, long leaseMillis) {
            this.leaseMillis = leaseMillis;
            String owner = clientId + ":" + Thread.currentThread().getId();
            long deadline = System.currentTimeMillis() + Math.max(waitMillis, 0);
            do {
                Long acquired = redisTemplate.execute(ACQUIRE_SCRIPT,
                        Collections.singletonList(key),
                        String.valueOf(leaseMillis), owner);
                if (acquired != null && acquired == 1L) {
                    acquiredOwner = owner;
                    startRenewal();
                    return true;
                }
                LockSupport.parkNanos(RETRY_INTERVAL_NANOS);
            } while (System.currentTimeMillis() < deadline);
            return false;
        }

        @Override
        public void unlock() {
            String owner = acquiredOwner;
            if (owner == null) {
                return;
            }
            if (!owner.equals(clientId + ":" + Thread.currentThread().getId())) {
                log.warn("Redis锁不属于当前线程，忽略解锁: key={}, owner={}", key, owner);
                return;
            }
            Long released = redisTemplate.execute(RELEASE_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(leaseMillis), owner);
            if (released != null && released >= 0) {
                stopRenewal();
                acquiredOwner = null;
            } else {
                log.warn("Redis锁释放失败或已过期: key={}", key);
            }
        }

        private void startRenewal() {
            if (renewTask != null) {
                return;
            }
            long interval = Math.max(leaseMillis / 3, 100L);
            renewTask = renewExecutor.scheduleWithFixedDelay(this::renew, interval, interval, TimeUnit.MILLISECONDS);
        }

        private void renew() {
            String owner = acquiredOwner;
            if (owner == null) {
                return;
            }
            try {
                redisTemplate.execute(RENEW_SCRIPT,
                        Collections.singletonList(key),
                        owner, String.valueOf(leaseMillis));
            } catch (Exception e) {
                log.warn("Redis锁续期失败: key={}, reason={}", key, e.getMessage());
            }
        }

        private void stopRenewal() {
            ScheduledFuture<?> task = renewTask;
            if (task != null) {
                task.cancel(false);
                renewTask = null;
            }
        }

        @Override
        public String getKey() {
            return key;
        }
    }
}
