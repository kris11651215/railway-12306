package com.railway.service.concurrency;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "railway.grab")
public class GrabProperties {

    private int queueCapacity = 200;
    private int workerCount = 4;
    private long lockWaitMillis = 3000;
    private long lockLeaseMillis = 5000;
    private long requestTtlMillis = 300000;
    private String orderStore = "memory";
    private long simulateOrderCostMillis = 0;

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    public long getLockWaitMillis() {
        return lockWaitMillis;
    }

    public void setLockWaitMillis(long lockWaitMillis) {
        this.lockWaitMillis = lockWaitMillis;
    }

    public long getLockLeaseMillis() {
        return lockLeaseMillis;
    }

    public void setLockLeaseMillis(long lockLeaseMillis) {
        this.lockLeaseMillis = lockLeaseMillis;
    }

    public long getRequestTtlMillis() {
        return requestTtlMillis;
    }

    public void setRequestTtlMillis(long requestTtlMillis) {
        this.requestTtlMillis = requestTtlMillis;
    }

    public String getOrderStore() {
        return orderStore;
    }

    public void setOrderStore(String orderStore) {
        this.orderStore = orderStore;
    }

    public long getSimulateOrderCostMillis() {
        return simulateOrderCostMillis;
    }

    public void setSimulateOrderCostMillis(long simulateOrderCostMillis) {
        this.simulateOrderCostMillis = simulateOrderCostMillis;
    }
}
