package com.finscope.web.response;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataQualityStatus;

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
        copyQuality(quote.getQualityStatus(), quote.getSourceCode(), quote.getAsOf(),
                quote.getRetrievedAt(), quote.getStaleAgeSeconds(), quote.getWarning(), quote.getRefreshId());
    }

    protected final void copyQuality(MarketDataQualityStatus status, String source,
                                     LocalDateTime dataAsOf, LocalDateTime fetchedAt, Long staleAge,
                                     String qualityWarning, String dataRefreshId) {
        qualityStatus = status == null ? null : status.name();
        sourceCode = source;
        asOf = dataAsOf;
        retrievedAt = fetchedAt;
        staleAgeSeconds = staleAge;
        warning = qualityWarning;
        refreshId = dataRefreshId;
    }

    public String getQualityStatus() { return qualityStatus; }
    public String getSourceCode() { return sourceCode; }
    public LocalDateTime getAsOf() { return asOf; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public Long getStaleAgeSeconds() { return staleAgeSeconds; }
    public String getWarning() { return warning; }
    public String getRefreshId() { return refreshId; }
}
