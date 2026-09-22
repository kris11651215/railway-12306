package com.railway.service.concurrency;

public interface DistributedLockManager {

    DistributedLock getLock(String key);
}
