package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.marketpulse.DailyResearchService;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.DailyResearchResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDate;

/** 市场日频研究查询，异常由既有全局处理器统一接管。 */
@RestController
public class DailyResearchController {
    @Resource
    private DailyResearchService service;

    @GetMapping("/api/market-pulse/research/{businessDate}")
    public ApiResponse<DailyResearchResponse> query(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponses.success(DailyResearchResponse.of(service.query(businessDate)));
    }
}
