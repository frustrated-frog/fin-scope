package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.watchlistresearch.WatchlistResearchScanService;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.WatchlistResearchScanResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;

@RestController
@RequestMapping("/api/watchlist/research-scan")
public class WatchlistResearchScanController {
    @Resource
    private WatchlistResearchScanService service;

    @GetMapping
    public ApiResponse<WatchlistResearchScanResponse> view() {
        return ApiResponses.success(WatchlistResearchScanResponse.of(service.view(false)));
    }

    @PostMapping
    public ApiResponse<WatchlistResearchScanResponse> refresh() {
        return ApiResponses.success(WatchlistResearchScanResponse.of(service.view(true)));
    }

    @GetMapping("/{id}")
    public ApiResponse<WatchlistResearchScanResponse.RunResponse> history(@PathVariable String id) {
        return ApiResponses.success(WatchlistResearchScanResponse.RunResponse.of(service.history(id)));
    }
}
