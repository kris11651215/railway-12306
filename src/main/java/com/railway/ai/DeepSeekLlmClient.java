package com.railway.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.railway.ai.tool.ToolCall;
import com.railway.ai.tool.ToolDefinition;
import com.railway.ai.tool.ToolResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeepSeekLlmClient implements LlmClient {

    private static final String SYSTEM_PROMPT = "你是铁路智慧出行服务平台的智能购票助手。"
            + "涉及车次、余票、票价、换乘、候补等实时数据时，必须调用提供的工具，禁止编造数据；"
            + "回答使用简洁的中文，可引用工具返回的真实字段。";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekLlmClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build();
    }

    @Override
    public String providerName() {
        return properties.getProvider();
    }

    @Override
    public List<ToolCall> planToolCalls(String userMessage, List<ToolDefinition> tools) {
        ObjectNode payload = buildPayload(userMessage, tools, null);
        return parseToolCalls(post(payload));
    }

    @Override
    public String composeAnswer(String userMessage, List<ToolCall> toolCalls, List<ToolResult> toolResults) {
        ObjectNode payload = buildPayload(userMessage, List.of(), formatResults(toolResults));
        JsonNode response = post(payload);
        return response.path("choices").path(0).path("message").path("content").asText("");
    }

    private ObjectNode buildPayload(String userMessage, List<ToolDefinition> tools, String toolResultsText) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", properties.getModel());
        payload.put("temperature", 0.2);
        ArrayNode messages = payload.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", SYSTEM_PROMPT);
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userMessage);
        if (toolResultsText != null) {
            ObjectNode data = messages.addObject();
            data.put("role", "user");
            data.put("content", "以下是通过后端接口查询到的真实数据，请严格基于这些数据用中文回答，不要编造：\n" + toolResultsText);
        }
        if (!tools.isEmpty()) {
            ArrayNode toolsNode = payload.putArray("tools");
            for (ToolDefinition definition : tools) {
                ObjectNode node = toolsNode.addObject();
                node.put("type", "function");
                ObjectNode function = node.putObject("function");
                function.put("name", definition.getName());
                function.put("description", definition.getDescription());
                function.set("parameters", objectMapper.valueToTree(definition.getParameters()));
            }
            payload.put("tool_choice", "auto");
        }
        return payload;
    }

    private List<ToolCall> parseToolCalls(JsonNode response) {
        JsonNode calls = response.path("choices").path(0).path("message").path("tool_calls");
        if (!calls.isArray()) {
            return List.of();
        }
        List<ToolCall> result = new ArrayList<>();
        for (JsonNode call : calls) {
            String name = call.path("function").path("name").asText("");
            String argumentsText = call.path("function").path("arguments").asText("{}");
            Map<String, Object> arguments = Map.of();
            try {
                arguments = objectMapper.readValue(argumentsText, new TypeReference<Map<String, Object>>() {
                });
            } catch (IOException ignored) {
            }
            if (!name.isBlank()) {
                result.add(new ToolCall(name, arguments));
            }
        }
        return result;
    }

    private String formatResults(List<ToolResult> toolResults) {
        StringBuilder builder = new StringBuilder();
        for (ToolResult result : toolResults) {
            builder.append("- 工具 ").append(result.getToolName())
                    .append("：").append(result.getSummary()).append('\n');
            if (result.getData() != null) {
                try {
                    builder.append(objectMapper.writeValueAsString(result.getData())).append('\n');
                } catch (IOException ignored) {
                }
            }
        }
        String text = builder.toString();
        return text.length() > 4000 ? text.substring(0, 4000) : text;
    }

    private JsonNode post(ObjectNode payload) {
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new LlmException("LLM请求序列化失败", e);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        LlmException last = null;
        for (int attempt = 0; attempt <= Math.max(properties.getMaxRetries(), 0); attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() / 100 == 2) {
                    return objectMapper.readTree(response.body());
                }
                last = new LlmException("LLM返回状态码 " + response.statusCode());
            } catch (IOException e) {
                last = new LlmException("LLM网络异常：" + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("LLM调用被中断", e);
            }
            sleep(200L * (attempt + 1));
        }
        throw last == null ? new LlmException("LLM调用失败") : last;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
