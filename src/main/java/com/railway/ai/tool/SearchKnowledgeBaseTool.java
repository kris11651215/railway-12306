package com.railway.ai.tool;

import com.railway.ai.rag.RagKnowledgeBase;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SearchKnowledgeBaseTool implements AiTool {

    private final RagKnowledgeBase ragKnowledgeBase;

    public SearchKnowledgeBaseTool(RagKnowledgeBase ragKnowledgeBase) {
        this.ragKnowledgeBase = ragKnowledgeBase;
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("question", Map.of("type", "string", "description", "客运规章、退票改签等自然语言问题"));
        return new ToolDefinition("search_knowledge_base", "在本地客运规章知识库中检索答案，返回依据与引用", Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("question")));
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String question = AiToolArguments.text(arguments, "question");
        if (question.isBlank()) {
            return ToolResult.failure(definition().getName(), "缺少问题内容");
        }
        try {
            var answer = ragKnowledgeBase.answer(question);
            return ToolResult.success(definition().getName(),
                    "命中 " + answer.getCitations().size() + " 条知识", answer);
        } catch (Exception e) {
            return ToolResult.failure(definition().getName(), "知识库检索失败：" + e.getMessage());
        }
    }
}
