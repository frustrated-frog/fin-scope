package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.instrument.StockFundamentalsService;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.StockFundamentalsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/watchlist/stocks/{code}")
public class WatchlistStockFundamentalsController {
    @Resource
    private StockFundamentalsService fundamentals;

    /** 按证券代码自动补齐股票财报；刷新失败以缺口和警告表达。 */
    @GetMapping("/fundamentals")
    public ApiResponse<StockFundamentalsResponse> fundamentals(
            @PathVariable String code, @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponses.success(StockFundamentalsResponse.of(fundamentals.load(code, refresh)));
    }
}
