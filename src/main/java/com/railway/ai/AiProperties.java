package com.railway.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "railway.ai")
public class AiProperties {

    private String mode = "mock";
    private String provider = "deepseek";
    private String baseUrl = "https://api.deepseek.com/v1";
    private String apiKey = "";
    private String model = "deepseek-chat";
    private int connectTimeoutMillis = 3000;
    private int readTimeoutMillis = 15000;
    private int maxRetries = 2;
    private String dataMode = "memory";
    private double backlogThreshold = 100.0;
    private int suggestTopN = 5;
    private boolean schedulerEnabled = true;
    private long candidateScanInitialDelayMillis = 60000;
    private long candidateScanIntervalMillis = 3600000;
    private int ragTopK = 3;
    private double ragMinScore = 0.05;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getDataMode() {
        return dataMode;
    }

    public void setDataMode(String dataMode) {
        this.dataMode = dataMode;
    }

    public double getBacklogThreshold() {
        return backlogThreshold;
    }

    public void setBacklogThreshold(double backlogThreshold) {
        this.backlogThreshold = backlogThreshold;
    }

    public int getSuggestTopN() {
        return suggestTopN;
    }

    public void setSuggestTopN(int suggestTopN) {
        this.suggestTopN = suggestTopN;
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }

    public long getCandidateScanInitialDelayMillis() {
        return candidateScanInitialDelayMillis;
    }

    public void setCandidateScanInitialDelayMillis(long candidateScanInitialDelayMillis) {
        this.candidateScanInitialDelayMillis = candidateScanInitialDelayMillis;
    }

    public long getCandidateScanIntervalMillis() {
        return candidateScanIntervalMillis;
    }

    public void setCandidateScanIntervalMillis(long candidateScanIntervalMillis) {
        this.candidateScanIntervalMillis = candidateScanIntervalMillis;
    }

    public int getRagTopK() {
        return ragTopK;
    }

    public void setRagTopK(int ragTopK) {
        this.ragTopK = ragTopK;
    }

    public double getRagMinScore() {
        return ragMinScore;
    }

    public void setRagMinScore(double ragMinScore) {
        this.ragMinScore = ragMinScore;
    }
}
