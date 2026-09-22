package com.railway.ai.tool;

import java.util.Map;

public interface AiTool {

    ToolDefinition definition();

    ToolResult execute(Map<String, Object> arguments);
}
