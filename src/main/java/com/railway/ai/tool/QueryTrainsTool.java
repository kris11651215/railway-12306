package com.railway.ai.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QueryTrainsTool implements AiTool {

    private final TrainQueryPort trainQueryPort;

    public QueryTrainsTool(TrainQueryPort trainQueryPort) {
        this.trainQueryPort = trainQueryPort;
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("from", Map.of("type", "string", "description", "出发站名，如 北京南"));
        properties.put("to", Map.of("type", "string", "description", "到达站名，如 上海虹桥"));
        properties.put("date", Map.of("type", "string", "description", "乘车日期 yyyy-MM-dd，默认当天"));
        properties.put("seatType", Map.of("type", "string", "description", "席别，默认二等座"));
        return new ToolDefinition("query_trains", "按出发站、到达站、日期查询可售车次与余票票价", Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("from", "to")));
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String from = AiToolArguments.text(arguments, "from");
        String to = AiToolArguments.text(arguments, "to");
        if (from.isBlank() || to.isBlank()) {
            return ToolResult.failure(definition().getName(), "缺少出发站或到达站");
        }
        try {
            var date = AiToolArguments.date(arguments, "date");
            String seatType = AiToolArguments.text(arguments, "seatType");
            var trains = trainQueryPort.query(from, to, date,
                    seatType.isBlank() ? null : seatType);
            return ToolResult.success(definition().getName(),
                    "查询到 " + trains.size() + " 趟车次", trains);
        } catch (Exception e) {
            return ToolResult.failure(definition().getName(), "车次查询失败：" + e.getMessage());
        }
    }
}
