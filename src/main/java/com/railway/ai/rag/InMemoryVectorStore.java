package com.railway.ai.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InMemoryVectorStore {

    private final HashingEmbedding embedding = new HashingEmbedding();
    private final List<Entry> entries = new ArrayList<>();

    public void add(KnowledgeDocument document, double[] vector) {
        entries.add(new Entry(document, vector));
    }

    public int size() {
        return entries.size();
    }

    public List<ScoredDocument> search(double[] queryVector, int topK) {
        List<ScoredDocument> scored = new ArrayList<>();
        for (Entry entry : entries) {
            scored.add(new ScoredDocument(entry.document, embedding.cosine(queryVector, entry.vector)));
        }
        scored.sort(Comparator.comparingDouble(ScoredDocument::getScore).reversed());
        if (topK > 0 && scored.size() > topK) {
            return new ArrayList<>(scored.subList(0, topK));
        }
        return scored;
    }

    private static class Entry {

        private final KnowledgeDocument document;
        private final double[] vector;

        Entry(KnowledgeDocument document, double[] vector) {
            this.document = document;
            this.vector = vector;
        }
    }

    public static class ScoredDocument {

        private final KnowledgeDocument document;
        private final double score;

        public ScoredDocument(KnowledgeDocument document, double score) {
            this.document = document;
            this.score = score;
        }

        public KnowledgeDocument getDocument() {
            return document;
        }

        public double getScore() {
            return score;
        }
    }
}
