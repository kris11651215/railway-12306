package com.railway.controller;

import com.railway.ai.AiChatService;
import com.railway.ai.rag.RagKnowledgeBase;
import com.railway.common.ApiResponse;
import com.railway.dto.ChatRequest;
import com.railway.dto.ChatResponseVO;
import com.railway.dto.KnowledgeAskRequest;
import com.railway.dto.RagAnswerVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService aiChatService;
    private final RagKnowledgeBase ragKnowledgeBase;

    public AiChatController(AiChatService aiChatService, RagKnowledgeBase ragKnowledgeBase) {
        this.aiChatService = aiChatService;
        this.ragKnowledgeBase = ragKnowledgeBase;
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponseVO> chat(@RequestBody ChatRequest request) {
        return ApiResponse.ok(aiChatService.chat(request));
    }

    @PostMapping("/knowledge/ask")
    public ApiResponse<RagAnswerVO> ask(@RequestBody KnowledgeAskRequest request) {
        String question = request == null ? null : request.getQuestion();
        return ApiResponse.ok(ragKnowledgeBase.answer(question));
    }
}
