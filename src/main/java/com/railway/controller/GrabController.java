package com.railway.controller;

import com.railway.common.ApiResponse;
import com.railway.dto.GrabResponse;
import com.railway.dto.GrabResultVO;
import com.railway.dto.GrabStatsVO;
import com.railway.dto.OrderCreateRequest;
import com.railway.dto.StockViewVO;
import com.railway.dto.StockWarmupRequest;
import com.railway.service.concurrency.GrabService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/order/grab")
public class GrabController {

    private final GrabService grabService;

    public GrabController(GrabService grabService) {
        this.grabService = grabService;
    }

    @PostMapping
    public ApiResponse<GrabResponse> grab(@RequestBody OrderCreateRequest request,
                                          @RequestParam(name = "sync", defaultValue = "false") boolean sync) {
        return ApiResponse.ok(grabService.grab(request, sync));
    }

    @GetMapping("/{requestId}")
    public ApiResponse<GrabResultVO> result(@PathVariable String requestId) {
        return ApiResponse.ok(grabService.getResult(requestId));
    }

    @GetMapping("/stats")
    public ApiResponse<GrabStatsVO> stats() {
        return ApiResponse.ok(grabService.stats());
    }

    @GetMapping("/stock")
    public ApiResponse<StockViewVO> stock(@RequestParam String trainNo,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate travelDate,
                                          @RequestParam String fromStation,
                                          @RequestParam String toStation,
                                          @RequestParam(required = false) String seatType) {
        return ApiResponse.ok(grabService.stockView(trainNo, travelDate, seatType, fromStation, toStation));
    }

    @PostMapping("/stock/warmup")
    public ApiResponse<StockViewVO> warmUpStock(@RequestBody StockWarmupRequest request) {
        return ApiResponse.ok(grabService.warmUp(request));
    }

    @PostMapping("/reset")
    public ApiResponse<Map<String, Object>> reset() {
        return ApiResponse.ok(grabService.reset());
    }
}
