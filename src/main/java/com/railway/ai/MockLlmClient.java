package com.railway.ai;

import com.railway.ai.tool.ToolCall;
import com.railway.ai.tool.ToolDefinition;
import com.railway.ai.tool.ToolResult;
import com.railway.dto.RagAnswerVO;
import com.railway.dto.TrainVO;
import com.railway.dto.TransferPlanVO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MockLlmClient implements LlmClient {

    public static final String TOOL_QUERY_TRAINS = "query_trains";
    public static final String TOOL_QUERY_TRANSFER = "query_transfer_routes";
    public static final String TOOL_KNOWLEDGE = "search_knowledge_base";

    private static final List<String> KNOWLEDGE_KEYWORDS = List.of(
            "退票", "改签", "规章", "规定", "儿童", "学生", "实名", "检票", "行李",
            "宠物", "报销", "发票", "身份证", "携带", "禁带", "候补规则", "候补怎么");
    private static final List<String> TRANSFER_KEYWORDS = List.of(
            "换乘", "中转", "怎么走", "路线", "最快", "最经济", "最便宜", "怎么去");
    private static final List<String> QUESTION_MARKERS = List.of(
            "吗", "怎么", "如何", "什么", "多久", "能不能", "可以", "是否", "？", "?");
    private static final Pattern FULL_DATE = Pattern.compile(
            "(\\d{4})\\s*[-/年]\\s*(\\d{1,2})\\s*[-/月]\\s*(\\d{1,2})\\s*[日号]?");
    private static final Pattern SHORT_DATE = Pattern.compile(
            "(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?");

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public List<ToolCall> planToolCalls(String userMessage, List<ToolDefinition> tools) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }
        String text = userMessage.trim();
        Set<String> names = tools.stream().map(ToolDefinition::getName).collect(Collectors.toSet());
        List<String> stations = StationAlias.extract(text);

        if (names.contains(TOOL_KNOWLEDGE) && containsAny(text, KNOWLEDGE_KEYWORDS)) {
            return List.of(new ToolCall(TOOL_KNOWLEDGE, Map.of("question", text)));
        }
        if (names.contains(TOOL_QUERY_TRANSFER) && containsAny(text, TRANSFER_KEYWORDS) && stations.size() >= 2) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("from", stations.get(0));
            arguments.put("to", stations.get(1));
            arguments.put("strategy", text.contains("最经济") || text.contains("最便宜") || text.contains("省钱")
                    ? "cheapest" : "fastest");
            LocalDate date = parseDate(text);
            if (date != null) {
                arguments.put("date", date.toString());
            }
            return List.of(new ToolCall(TOOL_QUERY_TRANSFER, arguments));
        }
        if (names.contains(TOOL_QUERY_TRAINS) && stations.size() >= 2) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("from", stations.get(0));
            arguments.put("to", stations.get(1));
            LocalDate date = parseDate(text);
            if (date != null) {
                arguments.put("date", date.toString());
            }
            return List.of(new ToolCall(TOOL_QUERY_TRAINS, arguments));
        }
        if (names.contains(TOOL_KNOWLEDGE) && containsAny(text, QUESTION_MARKERS)) {
            return List.of(new ToolCall(TOOL_KNOWLEDGE, Map.of("question", text)));
        }
        return List.of();
    }

    @Override
    public String composeAnswer(String userMessage, List<ToolCall> toolCalls, List<ToolResult> toolResults) {
        if (toolResults.isEmpty()) {
            return "我可以帮你查询车次、规划换乘路线、回答客运规章问题。"
                    + "请补充出发站、到达站和日期，例如：4月15日北京到上海。";
        }
        List<String> parts = new ArrayList<>();
        for (ToolResult result : toolResults) {
            if (!result.isSuccess()) {
                parts.add("【" + result.getToolName() + "】" + result.getSummary());
                continue;
            }
            switch (result.getToolName()) {
                case TOOL_QUERY_TRAINS -> parts.add(formatTrains(result));
                case TOOL_QUERY_TRANSFER -> parts.add(formatTransfer(result));
                case TOOL_KNOWLEDGE -> parts.add(formatKnowledge(result));
                default -> parts.add(result.getSummary());
            }
        }
        return String.join("\n", parts);
    }

    private String formatTrains(ToolResult result) {
        Object data = result.getData();
        if (!(data instanceof List<?> list) || list.isEmpty()) {
            return result.getSummary();
        }
        StringBuilder builder = new StringBuilder("已为你查询到 ").append(list.size()).append(" 趟车次：");
        for (Object item : list) {
            if (!(item instanceof TrainVO train)) {
                continue;
            }
            builder.append("\n- ").append(train.getTrainNo())
                    .append(" ").append(train.getDepartureTime()).append("-").append(train.getArrivalTime())
                    .append("，历时").append(train.getDurationMinutes()).append("分钟")
                    .append("，").append(train.getSeatType()).append("余").append(train.getRemainingCount()).append("张")
                    .append("，").append(train.getTotalPrice()).append("元");
        }
        return builder.toString();
    }

    private String formatTransfer(ToolResult result) {
        Object data = result.getData();
        if (!(data instanceof TransferPlanVO plan)) {
            return result.getSummary();
        }
        StringBuilder builder = new StringBuilder("为你规划了")
                .append(plan.getStrategyName()).append("方案（共")
                .append(plan.getTransferCount()).append("次换乘）：");
        int index = 1;
        for (TransferPlanVO.Leg leg : plan.getLegs()) {
            if (leg.getWaitBeforeMinutes() > 0) {
                builder.append("\n换乘等待 ").append(leg.getWaitBeforeMinutes()).append(" 分钟");
            }
            builder.append("\n").append(index++).append(". ").append(leg.getTrainNo())
                    .append(" ").append(leg.getFromStation()).append(" ")
                    .append(leg.getDepartureTime()).append(" → ")
                    .append(leg.getToStation()).append(" ").append(leg.getArrivalTime())
                    .append("（").append(leg.getDurationMinutes()).append("分钟，").append(leg.getPrice()).append("元）");
        }
        builder.append("\n总历时 ").append(plan.getTotalDurationMinutes()).append(" 分钟，总价 ")
                .append(plan.getTotalPrice()).append(" 元");
        return builder.toString();
    }

    private String formatKnowledge(ToolResult result) {
        Object data = result.getData();
        if (!(data instanceof RagAnswerVO answer)) {
            return result.getSummary();
        }
        return answer.getAnswer();
    }

    private LocalDate parseDate(String text) {
        Matcher full = FULL_DATE.matcher(text);
        if (full.find()) {
            return safeDate(Integer.parseInt(full.group(1)),
                    Integer.parseInt(full.group(2)), Integer.parseInt(full.group(3)));
        }
        Matcher shortDate = SHORT_DATE.matcher(text);
        if (shortDate.find()) {
            return safeDate(LocalDate.now().getYear(),
                    Integer.parseInt(shortDate.group(1)), Integer.parseInt(shortDate.group(2)));
        }
        return null;
    }

    private LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
