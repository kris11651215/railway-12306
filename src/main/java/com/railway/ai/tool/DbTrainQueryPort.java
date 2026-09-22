package com.railway.ai.tool;

import com.railway.dto.TrainVO;
import com.railway.service.TrainService;

import java.time.LocalDate;
import java.util.List;

public class DbTrainQueryPort implements TrainQueryPort {

    private final TrainService trainService;

    public DbTrainQueryPort(TrainService trainService) {
        this.trainService = trainService;
    }

    @Override
    public List<TrainVO> query(String from, String to, LocalDate travelDate, String seatType) {
        return trainService.queryTrains(from, to, travelDate, seatType, null);
    }
}
