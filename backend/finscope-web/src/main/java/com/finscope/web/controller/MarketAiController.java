package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.marketpulse.MarketAiService;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.MarketAiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/market-pulse/ai")
public class MarketAiController {
    @Resource
    private MarketAiService service;

    @GetMapping("/{date}")
    public ApiResponse<MarketAiResponse> read(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponses.success(MarketAiResponse.of(service.read(date)));
    }

    @PostMapping("/{date}")
    public ApiResponse<MarketAiResponse> request(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                               @RequestParam(defaultValue = "false") boolean retry) {
        return ApiResponses.success(MarketAiResponse.of(service.request(date, retry)));
    }
}
