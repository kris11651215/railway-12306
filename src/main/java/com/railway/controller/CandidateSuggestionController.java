package com.railway.controller;

import com.railway.ai.candidate.CandidateAnalysisService;
import com.railway.ai.candidate.CandidateSuggestionStore;
import com.railway.common.ApiResponse;
import com.railway.dto.CandidateSuggestionVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/ai")
public class CandidateSuggestionController {

    private final CandidateAnalysisService candidateAnalysisService;
    private final CandidateSuggestionStore suggestionStore;

    public CandidateSuggestionController(CandidateAnalysisService candidateAnalysisService,
                                         CandidateSuggestionStore suggestionStore) {
        this.candidateAnalysisService = candidateAnalysisService;
        this.suggestionStore = suggestionStore;
    }

    @GetMapping("/candidate-suggestion")
    public ApiResponse<CandidateSuggestionVO> suggestion(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate travelDate = date == null ? LocalDate.now() : date;
        CandidateSuggestionVO cached = suggestionStore.get(travelDate);
        if (cached != null) {
            return ApiResponse.ok(cached);
        }
        CandidateSuggestionVO suggestion = candidateAnalysisService.analyze(travelDate);
        suggestionStore.put(travelDate, suggestion);
        return ApiResponse.ok(suggestion);
    }
}
