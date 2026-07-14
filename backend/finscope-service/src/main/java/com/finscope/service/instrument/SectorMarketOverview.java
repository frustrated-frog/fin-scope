package com.finscope.service.instrument;

import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.service.marketdata.SectorCatalogGatewayResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 同一板块目录快照计算出的领涨与领跌榜。 */
public final class SectorMarketOverview {
    private final SectorCategory category;
    private final MarketDataQualityStatus qualityStatus;
    private final String sourceCode;
    private final LocalDateTime asOf;
    private final LocalDateTime retrievedAt;
    private final Long staleAgeSeconds;
    private final String warning;
    private final String refreshId;
    private final List<SectorMarketEntry> leaders;
    private final List<SectorMarketEntry> laggards;

    public SectorMarketOverview(SectorCategory category, MarketDataQualityStatus qualityStatus,
                                String sourceCode, LocalDateTime asOf, LocalDateTime retrievedAt,
                                Long staleAgeSeconds, String warning, String refreshId,
                                List<SectorMarketEntry> leaders, List<SectorMarketEntry> laggards) {
        this.category = category;
        this.qualityStatus = qualityStatus;
        this.sourceCode = sourceCode;
        this.asOf = asOf;
        this.retrievedAt = retrievedAt;
        this.staleAgeSeconds = staleAgeSeconds;
        this.warning = warning;
        this.refreshId = refreshId;
        this.leaders = immutable(leaders);
        this.laggards = immutable(laggards);
    }

    static SectorMarketOverview of(SectorCategory category, SectorCatalogGatewayResult result,
                                   List<SectorMarketEntry> leaders, List<SectorMarketEntry> laggards,
                                   String warning) {
        return new SectorMarketOverview(category, result.getQualityStatus(), result.getSourceCode(),
                result.getAsOf(), result.getRetrievedAt(), result.getStaleAgeSeconds(), warning,
                result.getRefreshId(), leaders, laggards);
    }

    private static List<SectorMarketEntry> immutable(List<SectorMarketEntry> values) {
        return Collections.unmodifiableList(new ArrayList<SectorMarketEntry>(values));
    }

    public SectorCategory getCategory() { return category; }
    public MarketDataQualityStatus getQualityStatus() { return qualityStatus; }
    public String getSourceCode() { return sourceCode; }
    public LocalDateTime getAsOf() { return asOf; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public Long getStaleAgeSeconds() { return staleAgeSeconds; }
    public String getWarning() { return warning; }
    public String getRefreshId() { return refreshId; }
    public List<SectorMarketEntry> getLeaders() { return leaders; }
    public List<SectorMarketEntry> getLaggards() { return laggards; }
}
