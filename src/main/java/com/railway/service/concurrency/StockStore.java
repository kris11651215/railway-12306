package com.railway.service.concurrency;

public interface StockStore {

    boolean warmUp(String key, int totalCount);

    void setStock(String key, int totalCount);

    StockDeductResult deduct(String key, int count);

    void compensate(String key, int count);

    Integer getRemaining(String key);

    boolean contains(String key);

    void clear();
}
