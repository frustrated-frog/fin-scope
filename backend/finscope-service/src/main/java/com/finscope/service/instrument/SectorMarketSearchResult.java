package com.finscope.service.instrument;

import com.finscope.domain.instrument.SectorMarketEntry;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 板块目录搜索结果及其数据质量。 */
public final class SectorMarketSearchResult {
    private final SectorMarketQualityStatus qualityStatus;
    private final LocalDateTime retrievedAt;
    private final String warning;
    private final List<SectorMarketEntry> items;

    public SectorMarketSearchResult(SectorMarketQualityStatus qualityStatus, LocalDateTime retrievedAt,
                                    String warning, List<SectorMarketEntry> items) {
        this.qualityStatus = qualityStatus;
        this.retrievedAt = retrievedAt;
        this.warning = warning;
        this.items = Collections.unmodifiableList(new ArrayList<SectorMarketEntry>(items));
    }

    public SectorMarketQualityStatus getQualityStatus() { return qualityStatus; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public String getWarning() { return warning; }
    public List<SectorMarketEntry> getItems() { return items; }
}
