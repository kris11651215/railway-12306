package com.railway.ai.candidate;

import com.railway.dto.CandidateBacklogVO;
import com.railway.mapper.CandidateMapper;

import java.time.LocalDate;
import java.util.List;

public class DbCandidateBacklogRepository implements CandidateBacklogRepository {

    private final CandidateMapper candidateMapper;

    public DbCandidateBacklogRepository(CandidateMapper candidateMapper) {
        this.candidateMapper = candidateMapper;
    }

    @Override
    public List<CandidateBacklogVO> aggregate(LocalDate travelDate) {
        return candidateMapper.aggregateWaitingByDate(travelDate);
    }
}
