package com.finscope.domain.instrument;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Provider 一次成功获取并解析的不可变板块目录快照。 */
public final class SectorMarketSnapshot {
    private final SectorCategory category;
    private final String providerCode;
    private final LocalDateTime retrievedAt;
    private final String payloadFingerprint;
    private final List<SectorMarketEntry> entries;
    private final List<String> warnings;

    public SectorMarketSnapshot(SectorCategory category, String providerCode, LocalDateTime retrievedAt,
                                String payloadFingerprint, List<SectorMarketEntry> entries, List<String> warnings) {
        this.category = category;
        this.providerCode = providerCode;
        this.retrievedAt = retrievedAt;
        this.payloadFingerprint = payloadFingerprint;
        this.entries = Collections.unmodifiableList(new ArrayList<SectorMarketEntry>(entries));
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public SectorCategory getCategory() { return category; }
    public String getProviderCode() { return providerCode; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public String getPayloadFingerprint() { return payloadFingerprint; }
    public List<SectorMarketEntry> getEntries() { return entries; }
    public List<String> getWarnings() { return warnings; }
}
