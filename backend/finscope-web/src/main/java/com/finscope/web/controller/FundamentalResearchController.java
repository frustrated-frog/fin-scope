package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.fundamentals.FundamentalResearchService;
import com.finscope.web.request.CreateFundamentalWatchRequest;
import com.finscope.web.request.ReviewFundamentalWatchRequest;
import com.finscope.web.request.SaveCompanyBusinessNotesRequest;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.FundamentalResearchResponse;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/watchlist/stocks/{code}/fundamental-research")
public class FundamentalResearchController {
    @Resource
    private FundamentalResearchService service;

    @GetMapping
    public ApiResponse<FundamentalResearchResponse> view(@PathVariable String code) {
        return ApiResponses.success(FundamentalResearchResponse.of(service.view(code)));
    }

    @PostMapping("/refresh")
    public ApiResponse<FundamentalResearchResponse> refresh(@PathVariable String code) {
        return ApiResponses.success(FundamentalResearchResponse.of(service.refresh(code)));
    }

    @PutMapping("/notes")
    public ApiResponse<FundamentalResearchResponse> notes(@PathVariable String code,
            @Valid @RequestBody SaveCompanyBusinessNotesRequest request) {
        return ApiResponses.success(FundamentalResearchResponse.of(service.saveNotes(code, request.toNotes())));
    }

    @PostMapping("/watches")
    public ApiResponse<FundamentalResearchResponse> addWatch(@PathVariable String code,
            @Valid @RequestBody CreateFundamentalWatchRequest request) {
        return ApiResponses.success(FundamentalResearchResponse.of(service.addWatch(code, request.toWatch())));
    }

    @PatchMapping("/watches/{watchId}/review")
    public ApiResponse<FundamentalResearchResponse> review(@PathVariable String code, @PathVariable long watchId,
            @Valid @RequestBody ReviewFundamentalWatchRequest request) {
        return ApiResponses.success(FundamentalResearchResponse.of(service.review(code, watchId,
                request.getRevision(), request.getStatus(), request.getNote().strip())));
    }
}
