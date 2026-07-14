package com.finscope.service.marketdata;

import com.finscope.domain.instrument.SectorMarketSnapshot;
import com.finscope.domain.marketdata.MarketDataQualityStatus;

import java.time.LocalDateTime;

/** 板块目录网关结果，携带可直接向页面透传的数据质量与溯源信息。 */
public final class SectorCatalogGatewayResult {
    private final SectorMarketSnapshot snapshot;
    private final MarketDataQualityStatus qualityStatus;
    private final String sourceCode;
    private final LocalDateTime asOf;
    private final LocalDateTime retrievedAt;
    private final Long staleAgeSeconds;
    private final String warning;
    private final String refreshId;

    public SectorCatalogGatewayResult(SectorMarketSnapshot snapshot, MarketDataQualityStatus qualityStatus,
                                      String sourceCode, LocalDateTime asOf, LocalDateTime retrievedAt,
                                      Long staleAgeSeconds, String warning, String refreshId) {
        this.snapshot = snapshot;
        this.qualityStatus = qualityStatus;
        this.sourceCode = sourceCode;
        this.asOf = asOf;
        this.retrievedAt = retrievedAt;
        this.staleAgeSeconds = staleAgeSeconds;
        this.warning = warning;
        this.refreshId = refreshId;
    }

    public SectorMarketSnapshot getSnapshot() { return snapshot; }
    public MarketDataQualityStatus getQualityStatus() { return qualityStatus; }
    public String getSourceCode() { return sourceCode; }
    public LocalDateTime getAsOf() { return asOf; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public Long getStaleAgeSeconds() { return staleAgeSeconds; }
    public String getWarning() { return warning; }
    public String getRefreshId() { return refreshId; }
}
