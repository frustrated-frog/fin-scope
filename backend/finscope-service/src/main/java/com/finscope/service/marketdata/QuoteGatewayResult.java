package com.finscope.service.marketdata;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataQualityStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 行情网关结果及一次刷新聚合质量。 */
public final class QuoteGatewayResult {
    private final List<Quote> quotes;
    private final MarketDataQualityStatus qualityStatus;
    private final String sourceCode;
    private final LocalDateTime asOf;
    private final LocalDateTime retrievedAt;
    private final Long staleAgeSeconds;
    private final String warning;
    private final String refreshId;

    public QuoteGatewayResult(List<Quote> quotes, MarketDataQualityStatus qualityStatus,
                              String sourceCode, LocalDateTime asOf, LocalDateTime retrievedAt,
                              Long staleAgeSeconds, String warning, String refreshId) {
        this.quotes = Collections.unmodifiableList(new ArrayList<Quote>(quotes));
        this.qualityStatus = qualityStatus;
        this.sourceCode = sourceCode;
        this.asOf = asOf;
        this.retrievedAt = retrievedAt;
        this.staleAgeSeconds = staleAgeSeconds;
        this.warning = warning;
        this.refreshId = refreshId;
    }

    public List<Quote> getQuotes() { return quotes; }
    public MarketDataQualityStatus getQualityStatus() { return qualityStatus; }
    public String getSourceCode() { return sourceCode; }
    public LocalDateTime getAsOf() { return asOf; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public Long getStaleAgeSeconds() { return staleAgeSeconds; }
    public String getWarning() { return warning; }
    public String getRefreshId() { return refreshId; }
}
