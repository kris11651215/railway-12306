package com.railway.ai;

import com.railway.ai.tool.ToolCall;
import com.railway.ai.tool.ToolDefinition;
import com.railway.ai.tool.ToolResult;

import java.util.List;

public interface LlmClient {

    String providerName();

    List<ToolCall> planToolCalls(String userMessage, List<ToolDefinition> tools);

    String composeAnswer(String userMessage, List<ToolCall> toolCalls, List<ToolResult> toolResults);
}
