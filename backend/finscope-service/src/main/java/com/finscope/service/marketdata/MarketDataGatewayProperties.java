package com.finscope.service.marketdata;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 仅供后台网关使用的可靠性参数。 */
@Component
public class MarketDataGatewayProperties {
    @Value("${finscope.market-data.fresh-cache-ms:15000}")
    private long freshCacheMs = 15_000L;
    @Value("${finscope.market-data.hedge-delay-ms:300}")
    private long hedgeDelayMs = 300L;
    @Value("${finscope.market-data.request-budget-ms:5000}")
    private long requestBudgetMs = 5_000L;
    @Value("${finscope.market-data.capital-request-budget-ms:20000}")
    private long capitalRequestBudgetMs = 20_000L;

    public MarketDataGatewayProperties() { }

    public MarketDataGatewayProperties(long freshCacheMs, long hedgeDelayMs, long requestBudgetMs) {
        this(freshCacheMs, hedgeDelayMs, requestBudgetMs, requestBudgetMs);
    }

    public MarketDataGatewayProperties(long freshCacheMs, long hedgeDelayMs, long requestBudgetMs,
                                       long capitalRequestBudgetMs) {
        if (freshCacheMs < 0 || hedgeDelayMs < 0 || requestBudgetMs <= 0) {
            throw new IllegalArgumentException("market data gateway durations are invalid");
        }
        if (capitalRequestBudgetMs <= 0) {
            throw new IllegalArgumentException("capital request budget must be positive");
        }
        this.freshCacheMs = freshCacheMs;
        this.hedgeDelayMs = hedgeDelayMs;
        this.requestBudgetMs = requestBudgetMs;
        this.capitalRequestBudgetMs = capitalRequestBudgetMs;
    }

    public long getFreshCacheMs() { return freshCacheMs; }
    public long getHedgeDelayMs() { return hedgeDelayMs; }
    public long getRequestBudgetMs() { return requestBudgetMs; }
    public long getCapitalRequestBudgetMs() { return capitalRequestBudgetMs; }
}
