package com.railway.ai.tool;

import com.railway.service.TransferRouteService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QueryTransferRoutesTool implements AiTool {

    private final TransferRouteService transferRouteService;

    public QueryTransferRoutesTool(TransferRouteService transferRouteService) {
        this.transferRouteService = transferRouteService;
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("from", Map.of("type", "string", "description", "出发站名"));
        properties.put("to", Map.of("type", "string", "description", "到达站名"));
        properties.put("strategy", Map.of("type", "string", "description", "fastest 最快 / cheapest 最经济"));
        properties.put("date", Map.of("type", "string", "description", "乘车日期 yyyy-MM-dd"));
        return new ToolDefinition("query_transfer_routes", "无直达车时规划中转换乘路径，支持最快与最经济策略", Map.of(
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
        String strategy = AiToolArguments.text(arguments, "strategy");
        try {
            var plan = transferRouteService.plan(from, to,
                    strategy.isBlank() ? "fastest" : strategy,
                    AiToolArguments.date(arguments, "date"));
            return ToolResult.success(definition().getName(),
                    "共 " + plan.getLegs().size() + " 程，换乘 " + plan.getTransferCount() + " 次", plan);
        } catch (Exception e) {
            return ToolResult.failure(definition().getName(), "换乘规划失败：" + e.getMessage());
        }
    }
}
