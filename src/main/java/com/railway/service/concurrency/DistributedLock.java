package com.railway.service.concurrency;

public interface DistributedLock {

    boolean tryLock(long waitMillis, long leaseMillis);

    void unlock();

    String getKey();
}
