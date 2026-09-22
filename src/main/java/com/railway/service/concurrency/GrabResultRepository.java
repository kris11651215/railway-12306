package com.railway.service.concurrency;

import com.railway.common.ErrorCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class GrabResultRepository {

    private final Map<String, GrabResult> results = new ConcurrentHashMap<>();
    private final AtomicLong submittedCount = new AtomicLong();
    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();
    private final AtomicLong rejectedCount = new AtomicLong();
    private volatile long firstSubmittedMillis;
    private volatile long lastFinishedMillis;

    public void markSubmitted(GrabResult result) {
        results.put(result.getRequestId(), result);
        if (submittedCount.getAndIncrement() == 0) {
            firstSubmittedMillis = result.getSubmittedAtMillis();
        }
    }

    public void markFinished(GrabResult result) {
        results.put(result.getRequestId(), result);
        if (result.getStatus() == GrabResultStatus.SUCCESS) {
            successCount.incrementAndGet();
        } else {
            failedCount.incrementAndGet();
            if (result.getCode() == ErrorCode.GRAB_QUEUE_FULL.getCode()) {
                rejectedCount.incrementAndGet();
            }
        }
        lastFinishedMillis = System.currentTimeMillis();
    }

    public GrabResult get(String requestId) {
        return results.get(requestId);
    }

    public long getSubmittedCount() {
        return submittedCount.get();
    }

    public long getSuccessCount() {
        return successCount.get();
    }

    public long getFailedCount() {
        return failedCount.get();
    }

    public long getRejectedCount() {
        return rejectedCount.get();
    }

    public long getFirstSubmittedMillis() {
        return firstSubmittedMillis;
    }

    public long getLastFinishedMillis() {
        return lastFinishedMillis;
    }

    public int getRetainedResultCount() {
        return results.size();
    }

    public void cleanup(long ttlMillis) {
        long deadline = System.currentTimeMillis() - ttlMillis;
        results.entrySet().removeIf(entry -> {
            GrabResult result = entry.getValue();
            return result.getFinishedAtMillis() > 0 && result.getFinishedAtMillis() < deadline;
        });
    }

    public void reset() {
        results.clear();
        submittedCount.set(0);
        successCount.set(0);
        failedCount.set(0);
        rejectedCount.set(0);
        firstSubmittedMillis = 0;
        lastFinishedMillis = 0;
    }
}
