package com.railway.service.concurrency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryStockStore implements StockStore {

    private final Map<String, AtomicInteger> stocks = new ConcurrentHashMap<>();

    @Override
    public boolean warmUp(String key, int totalCount) {
        return stocks.putIfAbsent(key, new AtomicInteger(totalCount)) == null;
    }

    @Override
    public void setStock(String key, int totalCount) {
        stocks.put(key, new AtomicInteger(totalCount));
    }

    @Override
    public StockDeductResult deduct(String key, int count) {
        AtomicInteger stock = stocks.get(key);
        if (stock == null) {
            return StockDeductResult.NOT_INITIALIZED;
        }
        while (true) {
            int current = stock.get();
            if (current < count) {
                return StockDeductResult.NO_STOCK;
            }
            if (stock.compareAndSet(current, current - count)) {
                return StockDeductResult.OK;
            }
        }
    }

    @Override
    public void compensate(String key, int count) {
        AtomicInteger stock = stocks.get(key);
        if (stock != null) {
            stock.addAndGet(count);
        }
    }

    @Override
    public Integer getRemaining(String key) {
        AtomicInteger stock = stocks.get(key);
        return stock == null ? null : stock.get();
    }

    @Override
    public boolean contains(String key) {
        return stocks.containsKey(key);
    }

    @Override
    public void clear() {
        stocks.clear();
    }
}
