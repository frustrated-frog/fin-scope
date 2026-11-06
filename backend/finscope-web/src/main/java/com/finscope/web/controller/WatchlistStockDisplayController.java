package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.instrument.StockMarketHistoryService;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.StockMarketHistoryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/watchlist/stocks/{code}")
public class WatchlistStockDisplayController {
    @Resource
    private StockMarketHistoryService marketHistory;

    @GetMapping("/market-history")
    public ApiResponse<StockMarketHistoryResponse> marketHistory(
            @PathVariable String code, @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponses.success(StockMarketHistoryResponse.of(marketHistory.load(code, refresh)));
    }
}
