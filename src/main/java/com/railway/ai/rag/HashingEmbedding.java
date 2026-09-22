package com.railway.ai.rag;

import org.springframework.stereotype.Component;

@Component
public class HashingEmbedding {

    public static final int DIMENSION = 512;

    public double[] embed(String text) {
        double[] vector = new double[DIMENSION];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String normalized = normalize(text);
        for (String token : bigrams(normalized)) {
            addToken(vector, token);
        }
        double norm = 0;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int index = 0; index < vector.length; index++) {
                vector[index] /= norm;
            }
        }
        return vector;
    }

    public double cosine(double[] left, double[] right) {
        double dot = 0;
        int length = Math.min(left.length, right.length);
        for (int index = 0; index < length; index++) {
            dot += left[index] * right[index];
        }
        return dot;
    }

    public java.util.Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return java.util.Set.of();
        }
        return bigrams(normalize(text));
    }

    private java.util.Set<String> bigrams(String normalized) {
        java.util.Set<String> tokens = new java.util.LinkedHashSet<>();
        if (normalized.isEmpty()) {
            return tokens;
        }
        if (normalized.length() == 1) {
            tokens.add(normalized);
            return tokens;
        }
        for (int index = 0; index + 2 <= normalized.length(); index++) {
            tokens.add(normalized.substring(index, index + 2));
        }
        return tokens;
    }

    private String normalize(String text) {
        StringBuilder builder = new StringBuilder();
        for (char ch : text.toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private void addToken(double[] vector, String token) {
        int hash = fnv1a(token);
        int slot = Math.floorMod(hash, DIMENSION);
        vector[slot] += (hash & 0x100) == 0 ? 1.0 : -1.0;
    }

    private int fnv1a(String token) {
        int hash = 0x811c9dc5;
        for (int index = 0; index < token.length(); index++) {
            hash ^= token.charAt(index);
            hash *= 0x01000193;
        }
        return hash;
    }
}
