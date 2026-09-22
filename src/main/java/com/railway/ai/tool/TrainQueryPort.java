package com.railway.ai.tool;

import com.railway.dto.TrainVO;

import java.time.LocalDate;
import java.util.List;

public interface TrainQueryPort {

    List<TrainVO> query(String from, String to, LocalDate travelDate, String seatType);
}
