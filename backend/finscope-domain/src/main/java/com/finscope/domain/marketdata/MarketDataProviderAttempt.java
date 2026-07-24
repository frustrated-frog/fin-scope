package com.finscope.domain.marketdata;

import java.time.LocalDateTime;

/** 一次刷新中对单个行情 Provider 的实际尝试记录。 */
public final class MarketDataProviderAttempt {
    private final long id;
    private final long refreshRunId;
    private final MarketDataCapability capability;
    private final String providerCode;
    private final String providerFamily;
    private final String status;
    private final String errorType;
    private final int retryCount;
    private final long latencyMs;
    private final int requestedCount;
    private final int acceptedCount;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;

    public MarketDataProviderAttempt(long id, long refreshRunId,
                                     MarketDataCapability capability,
                                     String providerCode, String providerFamily,
                                     String status, String errorType, int retryCount,
                                     long latencyMs, int requestedCount, int acceptedCount,
                                     LocalDateTime startedAt, LocalDateTime finishedAt) {
        this.id = id;
        this.refreshRunId = refreshRunId;
        this.capability = capability;
        this.providerCode = providerCode;
        this.providerFamily = providerFamily;
        this.status = status;
        this.errorType = errorType;
        this.retryCount = retryCount;
        this.latencyMs = latencyMs;
        this.requestedCount = requestedCount;
        this.acceptedCount = acceptedCount;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public long getId() { return id; }
    public long getRefreshRunId() { return refreshRunId; }
    public MarketDataCapability getCapability() { return capability; }
    public String getProviderCode() { return providerCode; }
    public String getProviderFamily() { return providerFamily; }
    public String getStatus() { return status; }
    public String getErrorType() { return errorType; }
    public int getRetryCount() { return retryCount; }
    public long getLatencyMs() { return latencyMs; }
    public int getRequestedCount() { return requestedCount; }
    public int getAcceptedCount() { return acceptedCount; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
}
