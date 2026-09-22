package com.railway.ai.candidate;

import com.railway.ai.AiProperties;
import com.railway.dto.CandidateSuggestionVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateAnalysisSchedulerTest {

    @Test
    void scanShouldStoreTodaySuggestion() {
        AiProperties properties = new AiProperties();
        CandidateAnalysisService service = new CandidateAnalysisService(
                new MemoryCandidateBacklogRepository(), properties);
        CandidateSuggestionStore store = new CandidateSuggestionStore();
        CandidateAnalysisScheduler scheduler = new CandidateAnalysisScheduler(service, store);

        scheduler.scan();

        CandidateSuggestionVO suggestion = store.get(LocalDate.now());
        assertNotNull(suggestion);
        assertTrue(suggestion.getScannedOdCount() > 0);
    }
}
