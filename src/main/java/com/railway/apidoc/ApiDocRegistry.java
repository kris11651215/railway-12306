package com.railway.apidoc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ApiDocRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, String> TAG_DESCRIPTIONS = Map.of(
            "基础", "环境自检与最小可用接口",
            "车次查询", "按 O-D 与日期查询车次、余票与票价",
            "订单", "下单扣库存与事务演示",
            "抢票", "高并发抢票、结果查询与库存管理",
            "路径规划", "中转换乘路径规划",
            "站内引导", "广州南站检票口与走行路线推荐",
            "AI", "智能购票助手、知识问答与候补加开建议",
            "接驳", "交通接驳智能引导",
            "运维", "监控指标端点",
            "API文档", "OpenAPI 文档与可视化页面");

    private final List<ApiEndpointDoc> endpoints;

    public ApiDocRegistry(List<ApiEndpointDoc> endpoints) {
        this.endpoints = List.copyOf(endpoints);
    }

    public List<ApiEndpointDoc> getEndpoints() {
        return endpoints;
    }

    public Set<String> documentedKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (ApiEndpointDoc endpoint : endpoints) {
            keys.add(endpoint.key());
        }
        return keys;
    }

    public ObjectNode toOpenApi() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("openapi", "3.0.3");
        ObjectNode info = root.putObject("info");
        info.put("title", "铁路智慧出行综合服务平台 API");
        info.put("version", "1.0.0");
        info.put("description", "车次查询、下单抢票、换乘规划、站内引导、AI 助手与运维监控接口。"
                + "统一响应结构 {code,message,data,timestamp}，详见 docs/architecture/02-api-design.md。");

        ObjectNode paths = root.putObject("paths");
        Set<String> tags = new LinkedHashSet<>();
        for (ApiEndpointDoc endpoint : endpoints) {
            ObjectNode pathNode = paths.has(endpoint.getPath())
                    ? (ObjectNode) paths.get(endpoint.getPath())
                    : paths.putObject(endpoint.getPath());
            pathNode.set(endpoint.getMethod().toLowerCase(), operation(endpoint));
            tags.add(endpoint.getTag());
        }

        ArrayNode tagArray = root.putArray("tags");
        for (String tag : tags) {
            ObjectNode tagNode = tagArray.addObject();
            tagNode.put("name", tag);
            tagNode.put("description", TAG_DESCRIPTIONS.getOrDefault(tag, ""));
        }
        return root;
    }

    private ObjectNode operation(ApiEndpointDoc endpoint) {
        ObjectNode operation = MAPPER.createObjectNode();
        operation.put("summary", endpoint.getSummary());
        operation.putArray("tags").add(endpoint.getTag());
        if (!endpoint.getParameters().isEmpty()) {
            ArrayNode parameterArray = operation.putArray("parameters");
            for (Map<String, Object> parameter : endpoint.getParameters()) {
                parameterArray.add(MAPPER.valueToTree(parameter));
            }
        }
        if (endpoint.getRequestBody() != null) {
            ObjectNode requestBody = operation.putObject("requestBody");
            requestBody.put("required", true);
            requestBody.putObject("content").putObject("application/json")
                    .set("schema", MAPPER.valueToTree(endpoint.getRequestBody()));
        }
        ObjectNode responses = operation.putObject("responses");
        ObjectNode response = responses.putObject("200");
        response.put("description", endpoint.getResponseDescription());
        response.putObject("content").putObject(endpoint.getResponseContentType())
                .set("schema", responseSchema(endpoint.getResponseContentType()));
        return operation;
    }

    private ObjectNode responseSchema(String contentType) {
        if (contentType != null && contentType.startsWith("text/")) {
            return MAPPER.createObjectNode().put("type", "string");
        }
        return defaultSchema();
    }

    private ObjectNode defaultSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("code").put("type", "integer");
        properties.putObject("message").put("type", "string");
        properties.putObject("data").put("type", "object");
        properties.putObject("timestamp").put("type", "integer");
        return schema;
    }
}
