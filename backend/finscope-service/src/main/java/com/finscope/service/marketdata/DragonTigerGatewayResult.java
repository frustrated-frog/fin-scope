package com.finscope.service.marketdata;

import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.rpc.marketintel.DragonTigerData;

import java.time.LocalDateTime;

public final class DragonTigerGatewayResult {
    private final DragonTigerData data;
    private final MarketDataQualityStatus qualityStatus;
    private final String sourceCode;
    private final LocalDateTime dataAsOf;
    private final LocalDateTime retrievedAt;
    private final Long staleAgeSeconds;
    private final String warning;
    private final String errorType;
    private final String refreshId;

    public DragonTigerGatewayResult(
            DragonTigerData data, MarketDataQualityStatus qualityStatus,
            String sourceCode, LocalDateTime dataAsOf, LocalDateTime retrievedAt,
            Long staleAgeSeconds, String warning, String errorType, String refreshId) {
        this.data = data;
        this.qualityStatus = qualityStatus;
        this.sourceCode = sourceCode;
        this.dataAsOf = dataAsOf;
        this.retrievedAt = retrievedAt;
        this.staleAgeSeconds = staleAgeSeconds;
        this.warning = warning;
        this.errorType = errorType;
        this.refreshId = refreshId;
    }

    public static DragonTigerGatewayResult freshPrimary(
            String sourceCode, DragonTigerData data, LocalDateTime retrievedAt, String refreshId) {
        return new DragonTigerGatewayResult(data, MarketDataQualityStatus.FRESH_PRIMARY,
                sourceCode, retrievedAt, retrievedAt, null, null, null, refreshId);
    }

    public static DragonTigerGatewayResult unavailable(
            String sourceCode, String warning, String refreshId) {
        return new DragonTigerGatewayResult(null, MarketDataQualityStatus.UNAVAILABLE,
                sourceCode, null, LocalDateTime.now(), null, warning,
                MarketDataQualityStatus.UNAVAILABLE.name(), refreshId);
    }

    public DragonTigerData getData() { return data; }
    public MarketDataQualityStatus getQualityStatus() { return qualityStatus; }
    public String getSourceCode() { return sourceCode; }
    public LocalDateTime getDataAsOf() { return dataAsOf; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public Long getStaleAgeSeconds() { return staleAgeSeconds; }
    public String getWarning() { return warning; }
    public String getErrorType() { return errorType; }
    public String getRefreshId() { return refreshId; }
}
