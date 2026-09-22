package com.railway.controller;

import com.railway.common.ApiResponse;
import com.railway.dto.StationRouteGuideVO;
import com.railway.service.StationRouteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/station")
public class StationRouteController {

    private final StationRouteService stationRouteService;

    public StationRouteController(StationRouteService stationRouteService) {
        this.stationRouteService = stationRouteService;
    }

    @GetMapping("/route-guide")
    public ApiResponse<StationRouteGuideVO> routeGuide(
            @RequestParam("trainNo") String trainNo,
            @RequestParam("formationLength") Integer formationLength,
            @RequestParam("trackNo") String trackNo,
            @RequestParam("entryDirection") String entryDirection,
            @RequestParam("passengerFloor") String passengerFloor,
            @RequestParam(value = "station", required = false) String station) {
        return ApiResponse.ok(stationRouteService.guide(station, trainNo, formationLength,
                trackNo, entryDirection, passengerFloor));
    }
}
