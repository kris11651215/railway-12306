package com.railway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "railway.route")
public class RouteProperties {

    private String mode = "memory";
    private String algorithm = "dijkstra";
    private int minTransferMinutes = 10;
    private int maxWaitMinutes = 240;
    private int maxTransfers = 2;
    private int maxDurationMinutes = 1440;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public int getMinTransferMinutes() {
        return minTransferMinutes;
    }

    public void setMinTransferMinutes(int minTransferMinutes) {
        this.minTransferMinutes = minTransferMinutes;
    }

    public int getMaxWaitMinutes() {
        return maxWaitMinutes;
    }

    public void setMaxWaitMinutes(int maxWaitMinutes) {
        this.maxWaitMinutes = maxWaitMinutes;
    }

    public int getMaxTransfers() {
        return maxTransfers;
    }

    public void setMaxTransfers(int maxTransfers) {
        this.maxTransfers = maxTransfers;
    }

    public int getMaxDurationMinutes() {
        return maxDurationMinutes;
    }

    public void setMaxDurationMinutes(int maxDurationMinutes) {
        this.maxDurationMinutes = maxDurationMinutes;
    }
}
