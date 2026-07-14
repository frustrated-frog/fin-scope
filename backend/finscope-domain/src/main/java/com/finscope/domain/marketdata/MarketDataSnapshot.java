package com.finscope.domain.marketdata;

import java.time.LocalDateTime;

/** 可跨进程重启使用的最后成功市场数据快照。 */
public final class MarketDataSnapshot {
    /**
     * 市场数据能力类型。
     */
    private final MarketDataCapability capability;
    /**
     * 数据作用域键。
     */
    private final String scopeKey;
    /**
     * 数据提供方编码。
     */
    private final String providerCode;
    /**
     * 数据提供方家族。
     */
    private final String providerFamily;
    /**
     * 数据对应时间。
     */
    private final LocalDateTime asOf;
    /**
     * 数据拉取时间。
     */
    private final LocalDateTime retrievedAt;
    /**
     * 原始载荷 JSON。
     */
    private final String payloadJson;
    /**
     * 原始载荷哈希。
     */
    private final String payloadHash;
    /**
     * 数据结构版本。
     */
    private final int schemaVersion;
    /**
     * 最近更新时间。
     */
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
