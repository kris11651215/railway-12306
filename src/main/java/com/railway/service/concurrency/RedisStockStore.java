package com.railway.service.concurrency;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.Set;

public class RedisStockStore implements StockStore {

    private static final RedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>("""
            local stock = redis.call('get', KEYS[1])
            if stock == false then
                return -1
            end
            if tonumber(stock) < tonumber(ARGV[1]) then
                return 0
            end
            redis.call('decrby', KEYS[1], ARGV[1])
            return 1
            """, Long.class);

    private static final String CLEAR_PATTERN = "railway:stock:*";

    private final StringRedisTemplate redisTemplate;

    public RedisStockStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean warmUp(String key, int totalCount) {
        Boolean created = redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(totalCount));
        return Boolean.TRUE.equals(created);
    }

    @Override
    public void setStock(String key, int totalCount) {
        redisTemplate.opsForValue().set(key, String.valueOf(totalCount));
    }

    @Override
    public StockDeductResult deduct(String key, int count) {
        Long result = redisTemplate.execute(DEDUCT_SCRIPT,
                Collections.singletonList(key), String.valueOf(count));
        if (result == null) {
            return StockDeductResult.NOT_INITIALIZED;
        }
        if (result == -1L) {
            return StockDeductResult.NOT_INITIALIZED;
        }
        if (result == 0L) {
            return StockDeductResult.NO_STOCK;
        }
        return StockDeductResult.OK;
    }

    @Override
    public void compensate(String key, int count) {
        redisTemplate.opsForValue().increment(key, count);
    }

    @Override
    public Integer getRemaining(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? null : Integer.valueOf(value);
    }

    @Override
    public boolean contains(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public void clear() {
        Set<String> keys = redisTemplate.keys(CLEAR_PATTERN);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
