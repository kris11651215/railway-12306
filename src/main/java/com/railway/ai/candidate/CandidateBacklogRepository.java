package com.railway.ai.candidate;

import com.railway.dto.CandidateBacklogVO;

import java.time.LocalDate;
import java.util.List;

public interface CandidateBacklogRepository {

    List<CandidateBacklogVO> aggregate(LocalDate travelDate);
}
