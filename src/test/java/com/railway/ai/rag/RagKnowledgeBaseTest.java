package com.railway.ai.rag;

import com.railway.ai.AiProperties;
import com.railway.common.ErrorCode;
import com.railway.dto.RagAnswerVO;
import com.railway.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagKnowledgeBaseTest {

    private RagKnowledgeBase knowledgeBase;

    @BeforeEach
    void setUp() {
        knowledgeBase = new RagKnowledgeBase(new HashingEmbedding(), new AiProperties(),
                new ClassPathResource("ai/knowledge-base.md"));
    }

    @Test
    void knowledgeBaseShouldLoadDocuments() {
        assertTrue(knowledgeBase.documentCount() >= 10);
    }

    @Test
    void refundQuestionShouldHitRefundDocument() {
        RagAnswerVO answer = knowledgeBase.answer("退票要提前几天，退票费怎么收");
        assertFalse(answer.getCitations().isEmpty());
        assertEquals("退票规则", answer.getCitations().get(0).getTitle());
        assertTrue(answer.getAnswer().contains("退票"));
    }

    @Test
    void childTicketQuestionShouldHitChildDocument() {
        RagAnswerVO answer = knowledgeBase.answer("儿童票几岁可以买儿童优惠票");
        assertFalse(answer.getCitations().isEmpty());
        assertTrue(answer.getCitations().get(0).getTitle().contains("儿童"));
    }

    @Test
    void unrelatedQuestionShouldReturnEmptyCitations() {
        RagAnswerVO answer = knowledgeBase.answer("量子计算机的量子比特原理");
        assertTrue(answer.getCitations().isEmpty());
        assertTrue(answer.getAnswer().contains("暂未找到"));
    }

    @Test
    void blankQuestionShouldFailWithParamError() {
        BusinessException exception = assertThrows(BusinessException.class, () -> knowledgeBase.answer(" "));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }
}
