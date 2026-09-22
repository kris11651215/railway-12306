package com.railway.ai.rag;

import com.railway.ai.AiProperties;
import com.railway.common.ErrorCode;
import com.railway.dto.RagAnswerVO;
import com.railway.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class RagKnowledgeBase {

    private static final int SNIPPET_LENGTH = 160;
    private static final int ANSWER_LENGTH = 300;
    private static final double TITLE_BOOST = 0.03;

    private final HashingEmbedding embedding;
    private final InMemoryVectorStore vectorStore;
    private final AiProperties properties;

    public RagKnowledgeBase(HashingEmbedding embedding, AiProperties properties,
                            @Value("classpath:ai/knowledge-base.md") Resource resource) {
        this.embedding = embedding;
        this.vectorStore = new InMemoryVectorStore();
        this.properties = properties;
        load(resource);
    }

    public RagAnswerVO answer(String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "question 为必填参数");
        }
        List<InMemoryVectorStore.ScoredDocument> scored = vectorStore.search(
                embedding.embed(question), properties.getRagTopK());
        List<InMemoryVectorStore.ScoredDocument> hits = new ArrayList<>();
        for (InMemoryVectorStore.ScoredDocument document : scored) {
            if (document.getScore() >= properties.getRagMinScore() && hasEnoughOverlap(question, document)) {
                hits.add(document);
            }
        }
        hits.sort(Comparator.comparingDouble(
                (InMemoryVectorStore.ScoredDocument document) -> rankedScore(question, document)).reversed());
        RagAnswerVO answer = new RagAnswerVO();
        answer.setQuestion(question);
        answer.setDegraded(false);
        if (hits.isEmpty()) {
            answer.setAnswer("知识库中暂未找到与「" + question + "」相关的规定，请咨询车站工作人员或拨打 12306。");
            answer.setCitations(List.of());
            return answer;
        }
        InMemoryVectorStore.ScoredDocument top = hits.get(0);
        StringBuilder text = new StringBuilder("根据《").append(top.getDocument().getTitle()).append("》：")
                .append(truncate(top.getDocument().getContent(), ANSWER_LENGTH));
        for (int index = 1; index < hits.size(); index++) {
            text.append("\n参考资料：《").append(hits.get(index).getDocument().getTitle()).append("》");
        }
        answer.setAnswer(text.toString());
        List<RagAnswerVO.Citation> citations = new ArrayList<>();
        for (InMemoryVectorStore.ScoredDocument document : hits) {
            citations.add(toCitation(document, rankedScore(question, document)));
        }
        answer.setCitations(citations);
        return answer;
    }

    public int documentCount() {
        return vectorStore.size();
    }

    private boolean hasEnoughOverlap(String question, InMemoryVectorStore.ScoredDocument scored) {
        java.util.Set<String> questionTokens = embedding.tokenize(question);
        if (questionTokens.isEmpty()) {
            return false;
        }
        java.util.Set<String> documentTokens = embedding.tokenize(
                scored.getDocument().getTitle() + scored.getDocument().getContent());
        int required = Math.min(2, questionTokens.size());
        int overlap = 0;
        for (String token : questionTokens) {
            if (documentTokens.contains(token)) {
                overlap++;
                if (overlap >= required) {
                    return true;
                }
            }
        }
        return false;
    }

    private double rankedScore(String question, InMemoryVectorStore.ScoredDocument scored) {
        java.util.Set<String> titleTokens = embedding.tokenize(scored.getDocument().getTitle());
        int titleHits = 0;
        for (String token : embedding.tokenize(question)) {
            if (titleTokens.contains(token)) {
                titleHits++;
            }
        }
        return scored.getScore() + titleHits * TITLE_BOOST;
    }

    private RagAnswerVO.Citation toCitation(InMemoryVectorStore.ScoredDocument scored, double score) {
        RagAnswerVO.Citation citation = new RagAnswerVO.Citation();
        citation.setId(scored.getDocument().getId());
        citation.setTitle(scored.getDocument().getTitle());
        citation.setScore(Math.round(score * 10000.0) / 10000.0);
        citation.setSnippet(truncate(scored.getDocument().getContent(), SNIPPET_LENGTH));
        return citation;
    }

    private String truncate(String content, int length) {
        String value = content.replace("\n", " ").trim();
        return value.length() <= length ? value : value.substring(0, length) + "…";
    }

    private void load(Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String title = null;
            StringBuilder content = new StringBuilder();
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("## ")) {
                    addDocument(index, title, content);
                    index++;
                    title = line.substring(3).trim();
                    content = new StringBuilder();
                } else if (title != null && !line.startsWith("# ")) {
                    content.append(line).append('\n');
                }
            }
            addDocument(index, title, content);
        } catch (Exception e) {
            throw new IllegalStateException("加载RAG知识库失败：" + e.getMessage(), e);
        }
    }

    private void addDocument(int index, String title, StringBuilder content) {
        if (title == null || content.toString().isBlank()) {
            return;
        }
        KnowledgeDocument document = new KnowledgeDocument(
                String.format("KB-%03d", index), title, content.toString().trim());
        vectorStore.add(document, embedding.embed(title + "\n" + document.getContent()));
    }
}
