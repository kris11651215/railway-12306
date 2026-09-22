package com.railway.ai;

public class LlmAnswer {

    private final String text;
    private final boolean degraded;
    private final String provider;

    public LlmAnswer(String text, boolean degraded, String provider) {
        this.text = text;
        this.degraded = degraded;
        this.provider = provider;
    }

    public String getText() {
        return text;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public String getProvider() {
        return provider;
    }
}
