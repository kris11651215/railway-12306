package com.railway.mapper;

import com.railway.dto.CandidateBacklogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CandidateMapper {

    List<CandidateBacklogVO> aggregateWaitingByDate(@Param("travelDate") LocalDate travelDate);
}
