package com.finscope.domain.marketdata;

import java.time.LocalDateTime;

/** 可跨进程重启使用的最后成功市场数据快照。 */
public final class MarketDataSnapshot {
    private final MarketDataCapability capability;
    private final String scopeKey;
    private final String providerCode;
    private final String providerFamily;
    private final LocalDateTime asOf;
    private final LocalDateTime retrievedAt;
    private final String payloadJson;
    private final String payloadHash;
    private final int schemaVersion;
    private final LocalDateTime updatedAt;

    public MarketDataSnapshot(MarketDataCapability capability, String scopeKey,
                              String providerCode, String providerFamily,
                              LocalDateTime asOf, LocalDateTime retrievedAt,
                              String payloadJson, String payloadHash,
                              int schemaVersion, LocalDateTime updatedAt) {
        if (capability == null || isBlank(scopeKey) || isBlank(providerCode)
                || isBlank(providerFamily) || retrievedAt == null || payloadJson == null
                || isBlank(payloadHash) || schemaVersion < 1 || updatedAt == null) {
            throw new IllegalArgumentException("market data snapshot is incomplete");
        }
        this.capability = capability;
        this.scopeKey = scopeKey;
        this.providerCode = providerCode;
        this.providerFamily = providerFamily;
        this.asOf = asOf;
        this.retrievedAt = retrievedAt;
        this.payloadJson = payloadJson;
        this.payloadHash = payloadHash;
        this.schemaVersion = schemaVersion;
        this.updatedAt = updatedAt;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public MarketDataCapability getCapability() { return capability; }
    public String getScopeKey() { return scopeKey; }
    public String getProviderCode() { return providerCode; }
    public String getProviderFamily() { return providerFamily; }
    public LocalDateTime getAsOf() { return asOf; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public String getPayloadJson() { return payloadJson; }
    public String getPayloadHash() { return payloadHash; }
    public int getSchemaVersion() { return schemaVersion; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
