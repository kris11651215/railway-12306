package com.railway.algorithm;

public class RouteOptions {

    private final int maxTransfers;
    private final int maxDurationMinutes;

    public RouteOptions(int maxTransfers, int maxDurationMinutes) {
        this.maxTransfers = maxTransfers;
        this.maxDurationMinutes = maxDurationMinutes;
    }

    public int getMaxTransfers() {
        return maxTransfers;
    }

    public int getMaxDurationMinutes() {
        return maxDurationMinutes;
    }
}
