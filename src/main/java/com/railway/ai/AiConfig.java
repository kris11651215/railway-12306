package com.railway.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railway.ai.candidate.CandidateBacklogRepository;
import com.railway.ai.candidate.DbCandidateBacklogRepository;
import com.railway.ai.candidate.MemoryCandidateBacklogRepository;
import com.railway.ai.tool.DbTrainQueryPort;
import com.railway.ai.tool.MemoryTrainQueryPort;
import com.railway.ai.tool.TrainQueryPort;
import com.railway.mapper.CandidateMapper;
import com.railway.service.TrainService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AiConfig {

    @Bean
    public LlmGateway llmGateway(AiProperties properties, ObjectMapper objectMapper) {
        MockLlmClient mock = new MockLlmClient();
        boolean remote = "remote".equalsIgnoreCase(properties.getMode())
                && properties.getApiKey() != null && !properties.getApiKey().isBlank();
        LlmClient primary = remote ? new DeepSeekLlmClient(properties, objectMapper) : mock;
        return new LlmGateway(primary, mock, properties.getMaxRetries());
    }

    @Bean
    @ConditionalOnProperty(name = "railway.ai.data-mode", havingValue = "db")
    public TrainQueryPort dbTrainQueryPort(TrainService trainService) {
        return new DbTrainQueryPort(trainService);
    }

    @Bean
    @ConditionalOnProperty(name = "railway.ai.data-mode", havingValue = "memory", matchIfMissing = true)
    public TrainQueryPort memoryTrainQueryPort() {
        return new MemoryTrainQueryPort();
    }

    @Bean
    @ConditionalOnProperty(name = "railway.ai.data-mode", havingValue = "db")
    public CandidateBacklogRepository dbCandidateBacklogRepository(CandidateMapper candidateMapper) {
        return new DbCandidateBacklogRepository(candidateMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "railway.ai.data-mode", havingValue = "memory", matchIfMissing = true)
    public CandidateBacklogRepository memoryCandidateBacklogRepository() {
        return new MemoryCandidateBacklogRepository();
    }
}
