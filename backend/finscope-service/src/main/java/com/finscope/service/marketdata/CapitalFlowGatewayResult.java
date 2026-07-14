package com.finscope.service.marketdata;

import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.rpc.marketintel.CapitalFlowData;

import java.time.LocalDateTime;

/** 资金流网关结果；旧资金快照由业务协调器判定并保留，不在此对象内伪装为新结果。 */
public final class CapitalFlowGatewayResult {
    private final CapitalFlowData data;
    private final MarketDataQualityStatus qualityStatus;
    private final String sourceCode;
    private final LocalDateTime retrievedAt;
    private final String warning;
    private final String errorType;
    private final String refreshId;

    public CapitalFlowGatewayResult(CapitalFlowData data, MarketDataQualityStatus qualityStatus,
                                    String sourceCode, LocalDateTime retrievedAt,
                                    String warning, String errorType, String refreshId) {
        this.data = data;
        this.qualityStatus = qualityStatus;
        this.sourceCode = sourceCode;
        this.retrievedAt = retrievedAt;
        this.warning = warning;
        this.errorType = errorType;
        this.refreshId = refreshId;
    }

    public static CapitalFlowGatewayResult freshPrimary(String sourceCode, CapitalFlowData data,
                                                        String warning, String refreshId) {
        return new CapitalFlowGatewayResult(data, MarketDataQualityStatus.FRESH_PRIMARY,
                sourceCode, LocalDateTime.now(), warning, null, refreshId);
    }

    public static CapitalFlowGatewayResult unavailable(String sourceCode, String warning, String refreshId) {
        return new CapitalFlowGatewayResult(null, MarketDataQualityStatus.UNAVAILABLE,
                sourceCode, LocalDateTime.now(), warning, MarketDataQualityStatus.UNAVAILABLE.name(), refreshId);
    }

    public CapitalFlowData getData() { return data; }
    public MarketDataQualityStatus getQualityStatus() { return qualityStatus; }
    public String getSourceCode() { return sourceCode; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public String getWarning() { return warning; }
    public String getErrorType() { return errorType; }
    public String getRefreshId() { return refreshId; }
}
