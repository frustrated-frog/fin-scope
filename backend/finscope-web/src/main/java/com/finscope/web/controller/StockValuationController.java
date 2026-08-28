package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.valuation.StockValuationView;
import com.finscope.service.valuation.StockValuationService;
import com.finscope.web.response.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/financials/instruments/{instrumentId}/valuation")
public class StockValuationController {
    @Resource
    private StockValuationService valuation;

    @GetMapping
    public ApiResponse<StockValuationView> view(@PathVariable Long instrumentId) {
        return ApiResponses.success(valuation.view(instrumentId));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<StockValuationView>> refresh(
            @PathVariable Long instrumentId) {
        return ResponseEntity.ok(ApiResponses.success(valuation.refresh(instrumentId)));
    }
}
