package com.railway.ai;

import com.railway.ai.tool.ToolDefinition;
import com.railway.ai.tool.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockLlmClientTest {

    private final MockLlmClient client = new MockLlmClient();
    private final List<ToolDefinition> tools = List.of(
            new ToolDefinition(MockLlmClient.TOOL_QUERY_TRAINS, "", Map.of()),
            new ToolDefinition(MockLlmClient.TOOL_QUERY_TRANSFER, "", Map.of()),
            new ToolDefinition(MockLlmClient.TOOL_KNOWLEDGE, "", Map.of()));

    @Test
    void shouldParseTrainQueryWithDateAndAlias() {
        List<ToolCall> calls = client.planToolCalls("4月15日北京到上海", tools);
        assertEquals(1, calls.size());
        assertEquals(MockLlmClient.TOOL_QUERY_TRAINS, calls.get(0).getName());
        assertEquals("北京南", calls.get(0).getArguments().get("from"));
        assertEquals("上海虹桥", calls.get(0).getArguments().get("to"));
        assertTrue(String.valueOf(calls.get(0).getArguments().get("date")).endsWith("-04-15"));
    }

    @Test
    void shouldParseTransferStrategy() {
        List<ToolCall> calls = client.planToolCalls("上海虹桥到杭州东最经济怎么走", tools);
        assertEquals(MockLlmClient.TOOL_QUERY_TRANSFER, calls.get(0).getName());
        assertEquals("cheapest", calls.get(0).getArguments().get("strategy"));
    }

    @Test
    void shouldRouteRegulationToKnowledge() {
        List<ToolCall> calls = client.planToolCalls("儿童票年龄怎么算", tools);
        assertEquals(MockLlmClient.TOOL_KNOWLEDGE, calls.get(0).getName());
        assertEquals("儿童票年龄怎么算", calls.get(0).getArguments().get("question"));
    }

    @Test
    void shouldReturnEmptyForSmallTalk() {
        assertTrue(client.planToolCalls("你好", tools).isEmpty());
        assertTrue(client.planToolCalls(null, tools).isEmpty());
    }

    @Test
    void stationExtractShouldPreferFullNames() {
        assertEquals(List.of("上海虹桥", "杭州东"), StationAlias.extract("从上海虹桥出发去杭州东"));
        assertEquals(List.of("北京南", "上海虹桥"), StationAlias.extract("北京到上海"));
        assertEquals("广州南", StationAlias.resolve("广州"));
    }
}
