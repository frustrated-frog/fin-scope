package com.finscope.service.marketdata;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 仅供后台网关使用的可靠性参数。 */
@Component
public class MarketDataGatewayProperties {
    @Value("${finscope.market-data.fresh-cache-ms:30000}")
    private long freshCacheMs = 30_000L;
    @Value("${finscope.market-data.hedge-delay-ms:800}")
    private long hedgeDelayMs = 800L;
    @Value("${finscope.market-data.request-budget-ms:10000}")
    private long requestBudgetMs = 10_000L;

    public MarketDataGatewayProperties() { }

    public MarketDataGatewayProperties(long freshCacheMs, long hedgeDelayMs, long requestBudgetMs) {
        if (freshCacheMs < 0 || hedgeDelayMs < 0 || requestBudgetMs <= 0) {
            throw new IllegalArgumentException("market data gateway durations are invalid");
        }
        this.freshCacheMs = freshCacheMs;
        this.hedgeDelayMs = hedgeDelayMs;
        this.requestBudgetMs = requestBudgetMs;
    }

    public long getFreshCacheMs() { return freshCacheMs; }
    public long getHedgeDelayMs() { return hedgeDelayMs; }
    public long getRequestBudgetMs() { return requestBudgetMs; }
}
