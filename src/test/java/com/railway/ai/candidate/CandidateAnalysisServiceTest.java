package com.railway.ai.candidate;

import com.railway.ai.AiProperties;
import com.railway.dto.CandidateBacklogVO;
import com.railway.dto.CandidateSuggestionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateAnalysisServiceTest {

    private CandidateAnalysisService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        service = new CandidateAnalysisService(new MemoryCandidateBacklogRepository(), properties);
    }

    @Test
    void sampleDateShouldProduceAddTrainSuggestion() {
        CandidateSuggestionVO suggestion = service.analyze(LocalDate.of(2026, 4, 15));
        assertEquals("2026-04-15", suggestion.getDate());
        assertEquals(5, suggestion.getScannedOdCount());
        assertTrue(suggestion.getOverThresholdCount() >= 3);
        CandidateSuggestionVO.Item top = suggestion.getItems().get(0);
        assertEquals("广州南", top.getFromStation());
        assertEquals("武汉", top.getToStation());
        assertEquals("ADD_TRAIN", top.getActionType());
        assertEquals("加开列车", top.getActionName());
        assertEquals("16节", top.getSuggestedFormation());
        assertTrue(top.getEstimatedLoadFactor() > 0 && top.getEstimatedLoadFactor() <= 0.98);
        assertTrue(top.getConfidence() > 0 && top.getConfidence() < 1);
        assertTrue(top.getReason().contains("建议加开"));
        assertTrue(suggestion.getSummary().contains("建议优先处理"));
    }

    @Test
    void suggestionShouldRespectGuangtieTwoHourDecisionWindow() {
        CandidateSuggestionVO suggestion = service.analyze(LocalDate.of(2026, 4, 15));
        assertEquals(30, suggestion.getReportReadyMinutes());
        assertEquals(120, suggestion.getDecisionWindowMinutes());
        assertEquals(Duration.ofMinutes(30),
                Duration.between(suggestion.getGeneratedAt(), suggestion.getReportDeadline()));
        assertEquals(Duration.ofMinutes(120),
                Duration.between(suggestion.getGeneratedAt(), suggestion.getDecisionDeadline()));
    }

    @Test
    void anyDateShouldGenerateDeterministicBacklog() {
        CandidateSuggestionVO first = service.analyze(LocalDate.of(2026, 10, 1));
        CandidateSuggestionVO second = service.analyze(LocalDate.of(2026, 10, 1));
        assertEquals(5, first.getScannedOdCount());
        assertEquals(first.getOverThresholdCount(), second.getOverThresholdCount());
        assertEquals(first.getItems().size(), second.getItems().size());
    }

    @Test
    void backlogIndexShouldApplySeatWeightAndUrgency() {
        CandidateBacklogVO backlog = new CandidateBacklogVO();
        backlog.setFromStation("广州南");
        backlog.setToStation("武汉");
        backlog.setSeatType("一等座");
        backlog.setWaitingCount(100);
        double farFuture = service.backlogIndex(backlog, LocalDate.now().plusDays(60));
        assertEquals(60.0, farFuture);
        double urgent = service.backlogIndex(backlog, LocalDate.now().plusDays(2));
        assertEquals(78.0, urgent);
    }

    @Test
    void lowBacklogShouldNotAppearInReport() {
        CandidateSuggestionVO suggestion = service.analyze(LocalDate.of(2026, 4, 15));
        assertFalse(suggestion.getItems().stream()
                .anyMatch(item -> "长沙南".equals(item.getFromStation()) && "广州南".equals(item.getToStation())));
    }
}
