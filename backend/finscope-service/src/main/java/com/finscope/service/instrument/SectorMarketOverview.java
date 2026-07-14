package com.finscope.service.instrument;

import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 同一板块目录快照计算出的领涨与领跌榜。 */
public final class SectorMarketOverview {
    private final SectorCategory category;
    private final SectorMarketQualityStatus qualityStatus;
    private final LocalDateTime retrievedAt;
    private final String warning;
    private final List<SectorMarketEntry> leaders;
    private final List<SectorMarketEntry> laggards;

    public SectorMarketOverview(SectorCategory category, SectorMarketQualityStatus qualityStatus,
                                LocalDateTime retrievedAt, String warning,
                                List<SectorMarketEntry> leaders, List<SectorMarketEntry> laggards) {
        this.category = category;
        this.qualityStatus = qualityStatus;
        this.retrievedAt = retrievedAt;
        this.warning = warning;
        this.leaders = immutable(leaders);
        this.laggards = immutable(laggards);
    }

    private static List<SectorMarketEntry> immutable(List<SectorMarketEntry> values) {
        return Collections.unmodifiableList(new ArrayList<SectorMarketEntry>(values));
    }

    public SectorCategory getCategory() { return category; }
    public SectorMarketQualityStatus getQualityStatus() { return qualityStatus; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public String getWarning() { return warning; }
    public List<SectorMarketEntry> getLeaders() { return leaders; }
    public List<SectorMarketEntry> getLaggards() { return laggards; }
}
