package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.supplychain.StockSupplyChainService;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.StockSupplyChainViewResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 股票产业链证据快照接口。 */
@RestController
@RequestMapping("/api/stocks/{code}/supply-chain")
public class StockSupplyChainController {
    private final StockSupplyChainService service;

    public StockSupplyChainController(StockSupplyChainService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<StockSupplyChainViewResponse> get(@PathVariable String code) {
        return ApiResponses.success(StockSupplyChainViewResponse.of(service.get(code)));
    }

    @PostMapping("/refresh")
    public ApiResponse<StockSupplyChainViewResponse.RefreshRunResponse> refresh(
            @PathVariable String code) {
        return ApiResponses.success(
                StockSupplyChainViewResponse.RefreshRunResponse.of(service.refresh(code)));
    }
}
