package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.marketpulse.MarketPanoramaService;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.MarketPanoramaFrameResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

@RestController
public class MarketPanoramaController {
    @Resource
    private MarketPanoramaService service;

    @GetMapping("/api/market-pulse/panorama")
    public ApiResponse<List<MarketPanoramaFrameResponse>> history(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam(defaultValue = "60") int limit) {
        return ApiResponses.success(service.history(businessDate, limit).stream()
                .map(MarketPanoramaFrameResponse::of).toList());
    }
}
