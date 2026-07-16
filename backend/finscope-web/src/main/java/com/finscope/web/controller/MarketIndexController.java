package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.service.instrument.MarketIndexService;
import com.finscope.web.response.MarketIndexQuoteResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/** 只读市场指数资源，不属于用户自选。 */
@RestController
@RequestMapping("/api/market-indices")
public class MarketIndexController {
    @Resource
    private MarketIndexService marketIndexService;

    /**
     * 查询市场指数行情列表。
     *
     * @param refresh 是否强制刷新行情数据。
     * @return 市场指数行情响应列表，包含指数基础信息和最新报价。
     */
    @GetMapping
    public ApiResponse<List<MarketIndexQuoteResponse>> list(@RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponses.success((refresh ? marketIndexService.list(true) : marketIndexService.list()).stream()
                .map(MarketIndexQuoteResponse::of)
                .collect(Collectors.toList()));
    }
}
