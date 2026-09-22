package com.railway.ai.tool;

public class ToolResult {

    private final String toolName;
    private final boolean success;
    private final String summary;
    private final Object data;

    public ToolResult(String toolName, boolean success, String summary, Object data) {
        this.toolName = toolName;
        this.success = success;
        this.summary = summary;
        this.data = data;
    }

    public static ToolResult success(String toolName, String summary, Object data) {
        return new ToolResult(toolName, true, summary, data);
    }

    public static ToolResult failure(String toolName, String summary) {
        return new ToolResult(toolName, false, summary, null);
    }

    public String getToolName() {
        return toolName;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getSummary() {
        return summary;
    }

    public Object getData() {
        return data;
    }
}
