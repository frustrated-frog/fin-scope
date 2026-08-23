package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.marketpulse.MarketPulseRefreshResult;
import com.finscope.service.marketpulse.MarketPulseService;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.MarketPulseWorkspaceResponse;
import com.finscope.web.response.MarketPulseBackfillResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/** 市场机会工作台查询与刷新接口。 */
@RestController
@RequestMapping("/api/market-pulse")
public class MarketPulseController {
    @Resource
    private MarketPulseService service;

    @GetMapping("/latest")
    public ApiResponse<MarketPulseWorkspaceResponse> latest() {
        return ApiResponses.success(MarketPulseWorkspaceResponse.of(service.latest()));
    }

    @GetMapping("/dates")
    public ApiResponse<List<String>> dates(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponses.success(service.dates(limit).stream().map(LocalDate::toString).collect(Collectors.toList()));
    }

    @GetMapping("/{businessDate}")
    public ApiResponse<MarketPulseWorkspaceResponse> detail(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponses.success(MarketPulseWorkspaceResponse.of(service.detail(businessDate)));
    }

    @PostMapping("/refresh")
    public ApiResponse<MarketPulseRefreshResult> refresh() {
        return ApiResponses.success(service.refresh());
    }

    @PostMapping("/backfill")
    public ApiResponse<MarketPulseBackfillResponse> backfill(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponses.success(MarketPulseBackfillResponse.of(service.backfill(startDate, endDate)));
    }
}
