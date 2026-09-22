package com.railway.ai.candidate;

import com.railway.dto.CandidateSuggestionVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@ConditionalOnProperty(name = "railway.ai.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class CandidateAnalysisScheduler {

    private static final Logger log = LoggerFactory.getLogger(CandidateAnalysisScheduler.class);

    private final CandidateAnalysisService candidateAnalysisService;
    private final CandidateSuggestionStore suggestionStore;

    public CandidateAnalysisScheduler(CandidateAnalysisService candidateAnalysisService,
                                      CandidateSuggestionStore suggestionStore) {
        this.candidateAnalysisService = candidateAnalysisService;
        this.suggestionStore = suggestionStore;
    }

    @Scheduled(initialDelayString = "${railway.ai.candidate-scan-initial-delay-millis:60000}",
            fixedDelayString = "${railway.ai.candidate-scan-interval-millis:3600000}")
    public void scan() {
        LocalDate today = LocalDate.now();
        try {
            CandidateSuggestionVO suggestion = candidateAnalysisService.analyze(today);
            suggestionStore.put(today, suggestion);
            log.info("候补定时扫描完成：date={}, 扫描方向={}, 超阈值={}",
                    today, suggestion.getScannedOdCount(), suggestion.getOverThresholdCount());
        } catch (Exception e) {
            log.error("候补定时扫描失败：date=" + today, e);
        }
    }
}
