package com.finscope.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.finscope.common.api.ApiResponse;
import com.finscope.domain.quant.statistics.FinancialStatisticsInput;
import com.finscope.service.quant.statistics.FinancialStatisticsService;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class FinancialStatisticsController {
    @Resource
    private FinancialStatisticsService statistics;

    @PostMapping("/api/quant/financial-statistics")
    public ApiResponse<JsonNode> analyze(@RequestBody FinancialStatisticsInput input) {
        return ApiResponses.success(statistics.analyze(input));
    }
}
