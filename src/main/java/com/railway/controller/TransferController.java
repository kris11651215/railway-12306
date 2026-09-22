package com.railway.controller;

import com.railway.common.ApiResponse;
import com.railway.dto.TransferPlanVO;
import com.railway.service.TransferRouteService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/routes")
public class TransferController {

    private final TransferRouteService transferRouteService;

    public TransferController(TransferRouteService transferRouteService) {
        this.transferRouteService = transferRouteService;
    }

    @GetMapping("/transfer")
    public ApiResponse<TransferPlanVO> plan(
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(value = "strategy", required = false, defaultValue = "fastest") String strategy,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(transferRouteService.plan(from, to, strategy, date));
    }
}
