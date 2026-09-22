package com.railway.controller;

import com.railway.common.ApiResponse;
import com.railway.dto.TrainVO;
import com.railway.service.TrainService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/trains")
public class TrainController {

    private final TrainService trainService;

    public TrainController(TrainService trainService) {
        this.trainService = trainService;
    }

    @GetMapping
    public ApiResponse<List<TrainVO>> queryTrains(
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "seatType", required = false) String seatType,
            @RequestParam(value = "trainType", required = false) String trainType) {
        return ApiResponse.ok(trainService.queryTrains(from, to, date, seatType, trainType));
    }
}
