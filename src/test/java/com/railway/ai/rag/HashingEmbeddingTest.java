package com.railway.ai.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashingEmbeddingTest {

    private final HashingEmbedding embedding = new HashingEmbedding();

    @Test
    void sameTextShouldBeFullySimilar() {
        double[] left = embedding.embed("退票规则说明");
        double[] right = embedding.embed("退票规则说明");
        assertEquals(1.0, embedding.cosine(left, right), 1e-9);
    }

    @Test
    void relatedTextShouldBeMoreSimilarThanUnrelated() {
        double[] refund = embedding.embed("退票规则说明");
        double[] refundVariant = embedding.embed("退票费怎么收");
        double[] unrelated = embedding.embed("量子计算机原理");
        assertTrue(embedding.cosine(refund, refundVariant) > embedding.cosine(refund, unrelated));
    }

    @Test
    void blankTextShouldReturnZeroVector() {
        double[] vector = embedding.embed("  ");
        double sum = 0;
        for (double value : vector) {
            sum += Math.abs(value);
        }
        assertEquals(0.0, sum);
    }
}
