package com.railway.ai;

import com.railway.ai.tool.AiToolRegistry;
import com.railway.ai.tool.AiTool;
import com.railway.ai.tool.MemoryTrainQueryPort;
import com.railway.ai.tool.QueryTrainsTool;
import com.railway.ai.tool.QueryTransferRoutesTool;
import com.railway.ai.tool.SearchKnowledgeBaseTool;
import com.railway.ai.rag.HashingEmbedding;
import com.railway.ai.rag.RagKnowledgeBase;
import com.railway.algorithm.AStarRoutePlanner;
import com.railway.algorithm.DijkstraRoutePlanner;
import com.railway.algorithm.MemoryTransferGraphProvider;
import com.railway.common.ErrorCode;
import com.railway.config.RouteProperties;
import com.railway.dto.ChatRequest;
import com.railway.dto.ChatResponseVO;
import com.railway.exception.BusinessException;
import com.railway.service.TransferRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiChatServiceTest {

    private AiChatService service;

    @BeforeEach
    void setUp() {
        MockLlmClient mock = new MockLlmClient();
        LlmGateway gateway = new LlmGateway(mock, mock, 0);
        AiProperties aiProperties = new AiProperties();
        RagKnowledgeBase knowledgeBase = new RagKnowledgeBase(new HashingEmbedding(), aiProperties,
                new ClassPathResource("ai/knowledge-base.md"));
        RouteProperties routeProperties = new RouteProperties();
        TransferRouteService transferRouteService = new TransferRouteService(
                new MemoryTransferGraphProvider(routeProperties),
                new DijkstraRoutePlanner(), new AStarRoutePlanner(), routeProperties);
        List<AiTool> tools = List.of(
                new QueryTrainsTool(new MemoryTrainQueryPort()),
                new QueryTransferRoutesTool(transferRouteService),
                new SearchKnowledgeBaseTool(knowledgeBase));
        service = new AiChatService(gateway, new AiToolRegistry(tools));
    }

    @Test
    void naturalLanguageShouldReturnStructuredTrains() {
        ChatRequest request = new ChatRequest();
        request.setMessage("4月15日北京到上海");
        ChatResponseVO response = service.chat(request);

        assertEquals("trains", response.getIntent().getType());
        assertEquals("北京南", response.getIntent().getFromStation());
        assertEquals("上海虹桥", response.getIntent().getToStation());
        assertNotNull(response.getTrains());
        assertTrue(response.getTrains().stream().anyMatch(train -> "G1".equals(train.getTrainNo())));
        assertTrue(response.getToolCalls().get(0).isSuccess());
        assertTrue(response.isDegraded());
        assertTrue(response.getReply().contains("车次"));
    }

    @Test
    void transferQuestionShouldCallTransferTool() {
        ChatRequest request = new ChatRequest();
        request.setMessage("上海虹桥到杭州东最快怎么走");
        ChatResponseVO response = service.chat(request);

        assertEquals("transfer", response.getIntent().getType());
        assertNotNull(response.getTransferPlan());
        assertEquals(2, response.getTransferPlan().getLegs().size());
        assertEquals("南京南", response.getTransferPlan().getTransferStations().get(0));
        assertTrue(response.getReply().contains("换乘"));
    }

    @Test
    void regulationQuestionShouldUseKnowledgeBase() {
        ChatRequest request = new ChatRequest();
        request.setMessage("退票要提前多久");
        ChatResponseVO response = service.chat(request);

        assertEquals("knowledge", response.getIntent().getType());
        assertNotNull(response.getKnowledge());
        assertFalse(response.getKnowledge().getCitations().isEmpty());
        assertTrue(response.getReply().contains("退票"));
    }

    @Test
    void unknownIntentShouldReturnGuide() {
        ChatRequest request = new ChatRequest();
        request.setMessage("你好");
        ChatResponseVO response = service.chat(request);

        assertEquals("unknown", response.getIntent().getType());
        assertTrue(response.getToolCalls().isEmpty());
        assertTrue(response.getReply().contains("查询车次"));
    }

    @Test
    void blankMessageShouldFailWithParamError() {
        ChatRequest request = new ChatRequest();
        request.setMessage("  ");
        BusinessException exception = assertThrows(BusinessException.class, () -> service.chat(request));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }
}
