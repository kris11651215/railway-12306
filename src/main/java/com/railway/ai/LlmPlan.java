package com.railway.ai;

import com.railway.ai.tool.ToolCall;

import java.util.List;

public class LlmPlan {

    private final List<ToolCall> toolCalls;
    private final boolean degraded;
    private final String provider;

    public LlmPlan(List<ToolCall> toolCalls, boolean degraded, String provider) {
        this.toolCalls = List.copyOf(toolCalls);
        this.degraded = degraded;
        this.provider = provider;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public String getProvider() {
        return provider;
    }
}
