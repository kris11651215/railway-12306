package com.railway.ai.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiToolRegistry {

    private final Map<String, AiTool> tools;

    public AiToolRegistry(List<AiTool> tools) {
        Map<String, AiTool> mapped = new LinkedHashMap<>();
        for (AiTool tool : tools) {
            mapped.put(tool.definition().getName(), tool);
        }
        this.tools = Map.copyOf(mapped);
    }

    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(AiTool::definition).toList();
    }

    public ToolResult execute(ToolCall call) {
        AiTool tool = tools.get(call.getName());
        if (tool == null) {
            return ToolResult.failure(call.getName(), "未知工具：" + call.getName());
        }
        try {
            return tool.execute(call.getArguments());
        } catch (Exception e) {
            return ToolResult.failure(call.getName(), "工具执行失败：" + e.getMessage());
        }
    }
}
