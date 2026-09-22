package com.railway.dto;

public class GrabStatsVO {

    private long submitted;
    private long success;
    private long failed;
    private long rejected;
    private long orders;
    private int queueSize;
    private int queueCapacity;
    private int workerCount;
    private long firstSubmittedMillis;
    private long lastFinishedMillis;
    private long elapsedMillis;
    private double qps;

    public long getSubmitted() {
        return submitted;
    }

    public void setSubmitted(long submitted) {
        this.submitted = submitted;
    }

    public long getSuccess() {
        return success;
    }

    public void setSuccess(long success) {
        this.success = success;
    }

    public long getFailed() {
        return failed;
    }

    public void setFailed(long failed) {
        this.failed = failed;
    }

    public long getRejected() {
        return rejected;
    }

    public void setRejected(long rejected) {
        this.rejected = rejected;
    }

    public long getOrders() {
        return orders;
    }

    public void setOrders(long orders) {
        this.orders = orders;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

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

    public long getFirstSubmittedMillis() {
        return firstSubmittedMillis;
    }

    public void setFirstSubmittedMillis(long firstSubmittedMillis) {
        this.firstSubmittedMillis = firstSubmittedMillis;
    }

    public long getLastFinishedMillis() {
        return lastFinishedMillis;
    }

    public void setLastFinishedMillis(long lastFinishedMillis) {
        this.lastFinishedMillis = lastFinishedMillis;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public void setElapsedMillis(long elapsedMillis) {
        this.elapsedMillis = elapsedMillis;
    }

    public double getQps() {
        return qps;
    }

    public void setQps(double qps) {
        this.qps = qps;
    }
}
