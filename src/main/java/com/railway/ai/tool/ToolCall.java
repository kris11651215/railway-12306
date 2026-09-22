package com.railway.ai.tool;

import java.util.Map;

public class ToolCall {

    private final String name;
    private final Map<String, Object> arguments;

    public ToolCall(String name, Map<String, Object> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }
}
