package com.railway.ai;

import com.railway.ai.tool.AiToolArguments;
import com.railway.ai.tool.AiToolRegistry;
import com.railway.ai.tool.ToolCall;
import com.railway.ai.tool.ToolResult;
import com.railway.common.ErrorCode;
import com.railway.dto.AiIntentVO;
import com.railway.dto.ChatRequest;
import com.railway.dto.ChatResponseVO;
import com.railway.dto.RagAnswerVO;
import com.railway.dto.ToolCallTraceVO;
import com.railway.dto.TrainVO;
import com.railway.dto.TransferPlanVO;
import com.railway.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiChatService {

    private final LlmGateway llmGateway;
    private final AiToolRegistry toolRegistry;

    public AiChatService(LlmGateway llmGateway, AiToolRegistry toolRegistry) {
        this.llmGateway = llmGateway;
        this.toolRegistry = toolRegistry;
    }

    public ChatResponseVO chat(ChatRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "message 为必填参数");
        }
        String message = request.getMessage().trim();
        LlmPlan plan = llmGateway.plan(message, toolRegistry.definitions());

        List<ToolResult> results = new ArrayList<>();
        List<ToolCallTraceVO> traces = new ArrayList<>();
        for (ToolCall call : plan.getToolCalls()) {
            ToolResult result = toolRegistry.execute(call);
            results.add(result);
            traces.add(toTrace(call, result));
        }
        LlmAnswer answer = llmGateway.compose(message, plan.getToolCalls(), results);

        ChatResponseVO response = new ChatResponseVO();
        response.setReply(answer.getText());
        response.setIntent(toIntent(plan.getToolCalls()));
        response.setToolCalls(traces);
        response.setTrains(findTrains(results));
        response.setTransferPlan(findTransferPlan(results));
        response.setKnowledge(findKnowledge(results));
        response.setDegraded(plan.isDegraded() || answer.isDegraded());
        response.setProvider(answer.getProvider());
        return response;
    }

    private ToolCallTraceVO toTrace(ToolCall call, ToolResult result) {
        ToolCallTraceVO trace = new ToolCallTraceVO();
        trace.setName(call.getName());
        trace.setArguments(call.getArguments());
        trace.setSuccess(result.isSuccess());
        trace.setSummary(result.getSummary());
        return trace;
    }

    private AiIntentVO toIntent(List<ToolCall> calls) {
        AiIntentVO intent = new AiIntentVO();
        intent.setType("unknown");
        if (calls.isEmpty()) {
            return intent;
        }
        ToolCall first = calls.get(0);
        intent.setType(switch (first.getName()) {
            case MockLlmClient.TOOL_QUERY_TRAINS -> "trains";
            case MockLlmClient.TOOL_QUERY_TRANSFER -> "transfer";
            case MockLlmClient.TOOL_KNOWLEDGE -> "knowledge";
            default -> "unknown";
        });
        intent.setFromStation(AiToolArguments.text(first.getArguments(), "from"));
        intent.setToStation(AiToolArguments.text(first.getArguments(), "to"));
        intent.setTravelDate(AiToolArguments.text(first.getArguments(), "date"));
        intent.setStrategy(AiToolArguments.text(first.getArguments(), "strategy"));
        return intent;
    }

    @SuppressWarnings("unchecked")
    private List<TrainVO> findTrains(List<ToolResult> results) {
        for (ToolResult result : results) {
            if (MockLlmClient.TOOL_QUERY_TRAINS.equals(result.getToolName())
                    && result.getData() instanceof List<?> list) {
                return (List<TrainVO>) list;
            }
        }
        return null;
    }

    private TransferPlanVO findTransferPlan(List<ToolResult> results) {
        for (ToolResult result : results) {
            if (MockLlmClient.TOOL_QUERY_TRANSFER.equals(result.getToolName())
                    && result.getData() instanceof TransferPlanVO plan) {
                return plan;
            }
        }
        return null;
    }

    private RagAnswerVO findKnowledge(List<ToolResult> results) {
        for (ToolResult result : results) {
            if (MockLlmClient.TOOL_KNOWLEDGE.equals(result.getToolName())
                    && result.getData() instanceof RagAnswerVO answer) {
                return answer;
            }
        }
        return null;
    }
}
