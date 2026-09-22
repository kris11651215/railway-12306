package com.railway.config;

import com.railway.apidoc.ApiDocRegistry;
import com.railway.apidoc.ApiEndpointDoc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class ApiDocConfig {

    private static final String JSON = "application/json";
    private static final String TEXT = "text/plain";

    private static final Map<String, Map<String, Object>> FIELD_SCHEMAS = Map.of(
            "userId", Map.of("type", "integer"),
            "totalCount", Map.of("type", "integer"),
            "formationLength", Map.of("type", "integer"),
            "trackNo", Map.of("type", "integer"),
            "sync", Map.of("type", "boolean"),
            "date", Map.of("type", "string", "format", "date"),
            "travelDate", Map.of("type", "string", "format", "date"));

    @Bean
    public ApiDocRegistry apiDocRegistry() {
        List<ApiEndpointDoc> endpoints = new ArrayList<>();
        endpoints.add(new ApiEndpointDoc("GET", "/hello", "基础",
                "环境自检：返回问候字符串", List.of(), null, "问候字符串", TEXT));
        endpoints.add(get("/api/trains", "车次查询", "按出发站、到达站、日期查询车次与余票票价",
                List.of(param("from", "query", true, "出发站名，如 北京南"),
                        param("to", "query", true, "到达站名，如 上海虹桥"),
                        param("date", "query", true, "乘车日期 yyyy-MM-dd"),
                        param("seatType", "query", false, "席别，默认二等座"),
                        param("trainType", "query", false, "车次等级 G/D/C/Z/T/K/P"))));
        endpoints.add(post("/api/orders", "订单", "创建订单：事务内锁定并扣减区间库存",
                body(List.of("userId", "trainNo", "travelDate", "fromStation", "toStation", "passengerName"),
                        List.of("userId", "用户ID"), List.of("trainNo", "车次号"), List.of("travelDate", "乘车日期"),
                        List.of("fromStation", "出发站"), List.of("toStation", "到达站"),
                        List.of("seatType", "席别，默认二等座（可选）"),
                        List.of("passengerName", "乘客姓名"), List.of("passengerIdCard", "证件号，入库脱敏（可选）"),
                        List.of("failAfterDeduct", "测试开关：扣库存后抛异常验证回滚（可选）"))));
        endpoints.add(new ApiEndpointDoc("POST", "/api/order/grab", "抢票",
                "高并发抢票：分布式锁 + 原子扣减 + 异步队列",
                List.of(param("sync", "query", false, "true 同步返回结果，默认 false 异步削峰")),
                body(List.of("userId", "trainNo", "travelDate", "fromStation", "toStation", "passengerName"),
                        List.of("userId", "用户ID"), List.of("trainNo", "车次号"), List.of("travelDate", "乘车日期"),
                        List.of("fromStation", "出发站"), List.of("toStation", "到达站"),
                        List.of("seatType", "席别，默认二等座（可选）"),
                        List.of("passengerName", "乘客姓名"),
                        List.of("passengerIdCard", "证件号，入库脱敏（可选）")),
                "受理结果或抢票结果", JSON));
        endpoints.add(get("/api/order/grab/{requestId}", "抢票", "查询异步抢票结果",
                List.of(param("requestId", "path", true, "抢票请求ID"))));
        endpoints.add(get("/api/order/grab/stats", "抢票", "压测统计：提交、成功、失败、QPS", List.of()));
        endpoints.add(get("/api/order/grab/stock", "抢票", "查询缓存库存",
                List.of(param("trainNo", "query", true, "车次号"),
                        param("travelDate", "query", true, "乘车日期"),
                        param("fromStation", "query", true, "出发站"),
                        param("toStation", "query", true, "到达站"),
                        param("seatType", "query", false, "席别"))));
        endpoints.add(post("/api/order/grab/stock/warmup", "抢票", "预热缓存库存",
                body(List.of("trainNo", "travelDate", "fromStation", "toStation", "totalCount"),
                        List.of("trainNo", "车次号"), List.of("travelDate", "乘车日期"),
                        List.of("fromStation", "出发站"), List.of("toStation", "到达站"),
                        List.of("seatType", "席别，默认二等座（可选）"),
                        List.of("totalCount", "库存总数"))));
        endpoints.add(new ApiEndpointDoc("POST", "/api/order/grab/reset", "抢票",
                "重置内存模式压测数据", List.of(), null, "重置结果", JSON));
        endpoints.add(get("/api/routes/transfer", "路径规划", "时间扩展图搜索最快/最经济换乘方案",
                List.of(param("from", "query", true, "出发站"),
                        param("to", "query", true, "到达站"),
                        param("strategy", "query", false, "fastest 最快 / cheapest 最经济"),
                        param("date", "query", false, "乘车日期"))));
        endpoints.add(get("/api/station/route-guide", "站内引导", "广州南站检票口与走行路线推荐",
                List.of(param("trainNo", "query", true, "车次号"),
                        param("formationLength", "query", true, "编组长度（辆）"),
                        param("trackNo", "query", true, "停靠股道 1-18"),
                        param("entryDirection", "query", true, "进站方向 北/南"),
                        param("passengerFloor", "query", true, "旅客楼层 2F/1F/3F/B1"),
                        param("station", "query", false, "车站名，默认广州南"))));
        endpoints.add(post("/api/ai/chat", "AI", "智能购票助手：自然语言查询车次/换乘/规章",
                body(List.of("message"), List.of("message", "用户自然语言"))));
        endpoints.add(post("/api/ai/knowledge/ask", "AI", "客运规章 RAG 问答",
                body(List.of("question"), List.of("question", "客运规章问题"))));
        endpoints.add(get("/api/ai/candidate-suggestion", "AI", "候补积压分析与加开建议报告",
                List.of(param("date", "query", false, "乘车日期，默认当天"))));
        endpoints.add(get("/api/travel-guide", "接驳", "交通接驳智能引导",
                List.of(param("trainNo", "query", true, "车次号"),
                        param("fromStation", "query", true, "出发站"),
                        param("userLocation", "query", false, "用户位置，如 地铁2号线"))));
        endpoints.add(new ApiEndpointDoc("GET", "/internal/metrics", "运维",
                "Prometheus 文本格式监控指标", List.of(), null, "Prometheus 文本指标", TEXT));
        endpoints.add(get("/v3/api-docs", "API文档", "OpenAPI 3.0 JSON 描述", List.of()));
        endpoints.add(get("/swagger-ui.html", "API文档", "离线可视化 API 文档页面", List.of()));
        return new ApiDocRegistry(endpoints);
    }

    private ApiEndpointDoc get(String path, String tag, String summary, List<Map<String, Object>> parameters) {
        return new ApiEndpointDoc("GET", path, tag, summary, parameters, null, "成功", JSON);
    }

    private ApiEndpointDoc post(String path, String tag, String summary, Map<String, Object> requestBody) {
        return new ApiEndpointDoc("POST", path, tag, summary, List.of(), requestBody, "成功", JSON);
    }

    private Map<String, Object> param(String name, String in, boolean required, String description) {
        Map<String, Object> parameter = new LinkedHashMap<>();
        parameter.put("name", name);
        parameter.put("in", in);
        parameter.put("required", required);
        parameter.put("description", description);
        parameter.put("schema", schemaOf(name));
        return parameter;
    }

    private Map<String, Object> schemaOf(String name) {
        return FIELD_SCHEMAS.getOrDefault(name, Map.of("type", "string"));
    }

    @SafeVarargs
    private Map<String, Object> body(List<String> requiredFields, List<String>... fields) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        for (List<String> field : fields) {
            String name = field.get(0);
            String description = field.size() > 1 ? field.get(1) : "";
            Map<String, Object> property = new LinkedHashMap<>(schemaOf(name));
            property.put("description", description);
            properties.put(name, property);
        }
        schema.put("properties", properties);
        schema.put("required", requiredFields);
        return schema;
    }
}
