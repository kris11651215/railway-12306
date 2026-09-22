package com.railway.ai;

import com.railway.ai.tool.ToolCall;
import com.railway.ai.tool.ToolDefinition;
import com.railway.ai.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    private final LlmClient primary;
    private final LlmClient fallback;
    private final int maxRetries;
    private final boolean localOnly;

    public LlmGateway(LlmClient primary, LlmClient fallback, int maxRetries) {
        this.primary = primary;
        this.fallback = fallback;
        this.maxRetries = Math.max(maxRetries, 0);
        this.localOnly = primary == fallback;
    }

    public LlmPlan plan(String userMessage, List<ToolDefinition> tools) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return new LlmPlan(primary.planToolCalls(userMessage, tools), localOnly, primary.providerName());
            } catch (Exception e) {
                log.warn("LLM意图解析失败，第{}次重试：{}", attempt + 1, e.getMessage());
            }
        }
        log.warn("LLM意图解析降级为本地Mock：primary={}", primary.providerName());
        return new LlmPlan(fallback.planToolCalls(userMessage, tools), true, fallback.providerName());
    }

    public LlmAnswer compose(String userMessage, List<ToolCall> toolCalls, List<ToolResult> toolResults) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return new LlmAnswer(primary.composeAnswer(userMessage, toolCalls, toolResults),
                        localOnly, primary.providerName());
            } catch (Exception e) {
                log.warn("LLM回答生成失败，第{}次重试：{}", attempt + 1, e.getMessage());
            }
        }
        log.warn("LLM回答生成降级为本地Mock：primary={}", primary.providerName());
        return new LlmAnswer(fallback.composeAnswer(userMessage, toolCalls, toolResults),
                true, fallback.providerName());
    }

    public String primaryProvider() {
        return primary.providerName();
    }
}
