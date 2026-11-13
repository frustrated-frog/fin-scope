package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.fundamentals.FundamentalBusinessResearchService;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.FundamentalBusinessResearchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;

@RestController
@RequestMapping("/api/watchlist/stocks/{code}/fundamental-research/business-research")
public class FundamentalBusinessResearchController {
    @Resource
    private FundamentalBusinessResearchService service;

    @GetMapping
    public ApiResponse<FundamentalBusinessResearchResponse> view(@PathVariable String code) {
        return ApiResponses.success(FundamentalBusinessResearchResponse.of(service.view(code)));
    }

    @PostMapping("/runs")
    public ApiResponse<FundamentalBusinessResearchResponse> start(@PathVariable String code) {
        return ApiResponses.success(FundamentalBusinessResearchResponse.of(service.start(code)));
    }
}
