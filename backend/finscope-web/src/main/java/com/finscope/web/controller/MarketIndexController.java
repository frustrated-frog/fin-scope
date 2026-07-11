package com.finscope.web.controller;

import com.finscope.service.instrument.MarketIndexService;
import com.finscope.web.response.MarketIndexQuoteResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/** 只读市场指数资源，不属于用户自选。 */
@RestController
@RequestMapping("/api/market-indices")
public class MarketIndexController {
    @Resource
    private MarketIndexService marketIndexService;

    @GetMapping
    public List<MarketIndexQuoteResponse> list() {
        return marketIndexService.list().stream()
                .map(MarketIndexQuoteResponse::of)
                .collect(Collectors.toList());
    }
}
