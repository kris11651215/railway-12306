package com.railway.controller;

import com.railway.ai.travel.TravelGuideService;
import com.railway.common.ApiResponse;
import com.railway.dto.TravelGuideVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TravelGuideController {

    private final TravelGuideService travelGuideService;

    public TravelGuideController(TravelGuideService travelGuideService) {
        this.travelGuideService = travelGuideService;
    }

    @GetMapping("/travel-guide")
    public ApiResponse<TravelGuideVO> guide(
            @RequestParam("trainNo") String trainNo,
            @RequestParam("fromStation") String fromStation,
            @RequestParam(value = "userLocation", required = false) String userLocation) {
        return ApiResponse.ok(travelGuideService.guide(trainNo, fromStation, userLocation));
    }
}
