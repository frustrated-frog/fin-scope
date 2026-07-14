package com.finscope.web.response;

import com.finscope.domain.instrument.Quote;

import java.time.LocalDateTime;

/** 行情类 REST 响应共用的只读质量与溯源字段。 */
abstract class MarketDataQualityResponse {
    private String qualityStatus;
    private String sourceCode;
    private LocalDateTime asOf;
    private LocalDateTime retrievedAt;
    private Long staleAgeSeconds;
    private String warning;
    private String refreshId;

    protected final void copyQuality(Quote quote) {
        if (quote == null) return;
        qualityStatus = quote.getQualityStatus() == null ? null : quote.getQualityStatus().name();
        sourceCode = quote.getSourceCode();
        asOf = quote.getAsOf();
        retrievedAt = quote.getRetrievedAt();
        staleAgeSeconds = quote.getStaleAgeSeconds();
        warning = quote.getWarning();
        refreshId = quote.getRefreshId();
    }

    public String getQualityStatus() { return qualityStatus; }
    public String getSourceCode() { return sourceCode; }
    public LocalDateTime getAsOf() { return asOf; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public Long getStaleAgeSeconds() { return staleAgeSeconds; }
    public String getWarning() { return warning; }
    public String getRefreshId() { return refreshId; }
}
