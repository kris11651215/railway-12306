package com.railway.ai.candidate;

import com.railway.dto.CandidateSuggestionVO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CandidateSuggestionStore {

    private final Map<String, CandidateSuggestionVO> store = new ConcurrentHashMap<>();

    public void put(LocalDate date, CandidateSuggestionVO suggestion) {
        store.put(date.toString(), suggestion);
    }

    public CandidateSuggestionVO get(LocalDate date) {
        return store.get(date.toString());
    }
}
