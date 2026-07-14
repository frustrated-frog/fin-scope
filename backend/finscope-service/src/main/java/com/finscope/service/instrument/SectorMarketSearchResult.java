package com.finscope.service.instrument;

import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.service.marketdata.SectorCatalogGatewayResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 板块目录搜索结果及其聚合数据质量。 */
public final class SectorMarketSearchResult {
    private final MarketDataQualityStatus qualityStatus;
    private final String sourceCode;
    private final LocalDateTime asOf;
    private final LocalDateTime retrievedAt;
    private final Long staleAgeSeconds;
    private final String warning;
    private final String refreshId;
    private final List<SectorMarketEntry> items;

    public SectorMarketSearchResult(MarketDataQualityStatus qualityStatus, String sourceCode,
                                    LocalDateTime asOf, LocalDateTime retrievedAt, Long staleAgeSeconds,
                                    String warning, String refreshId, List<SectorMarketEntry> items) {
        this.qualityStatus = qualityStatus;
        this.sourceCode = sourceCode;
        this.asOf = asOf;
        this.retrievedAt = retrievedAt;
        this.staleAgeSeconds = staleAgeSeconds;
        this.warning = warning;
        this.refreshId = refreshId;
        this.items = Collections.unmodifiableList(new ArrayList<SectorMarketEntry>(items));
    }

    static SectorMarketSearchResult of(List<SectorCatalogGatewayResult> results,
                                       List<SectorMarketEntry> items) {
        int available = 0;
        int stale = 0;
        int fallback = 0;
        LocalDateTime asOf = null;
        LocalDateTime retrievedAt = null;
        Long staleAge = null;
        Set<String> sources = new LinkedHashSet<String>();
        Set<String> warnings = new LinkedHashSet<String>();
        Set<String> refreshIds = new LinkedHashSet<String>();
        for (SectorCatalogGatewayResult result : results) {
            if (result.getSnapshot() != null) {
                available++;
                if (result.getQualityStatus() == MarketDataQualityStatus.STALE_FALLBACK) stale++;
                if (result.getQualityStatus() == MarketDataQualityStatus.FRESH_FALLBACK) fallback++;
            }
            if (result.getSourceCode() != null) sources.add(result.getSourceCode());
            if (result.getWarning() != null) warnings.add(result.getWarning());
            if (result.getRefreshId() != null) refreshIds.add(result.getRefreshId());
            asOf = earlier(asOf, result.getAsOf());
            retrievedAt = earlier(retrievedAt, result.getRetrievedAt());
            if (result.getStaleAgeSeconds() != null) {
                staleAge = staleAge == null ? result.getStaleAgeSeconds()
                        : Math.max(staleAge, result.getStaleAgeSeconds());
            }
        }
        MarketDataQualityStatus status = available == 0 ? MarketDataQualityStatus.UNAVAILABLE
                : available < results.size() ? MarketDataQualityStatus.PARTIAL_FRESH
                : stale == available ? MarketDataQualityStatus.STALE_FALLBACK
                : stale > 0 ? MarketDataQualityStatus.PARTIAL_FRESH
                : fallback > 0 ? MarketDataQualityStatus.FRESH_FALLBACK
                : MarketDataQualityStatus.FRESH_PRIMARY;
        return new SectorMarketSearchResult(status, join(sources), asOf, retrievedAt, staleAge,
                join(warnings), join(refreshIds), items);
    }

    private static LocalDateTime earlier(LocalDateTime current, LocalDateTime candidate) {
        if (candidate == null) return current;
        return current == null || candidate.isBefore(current) ? candidate : current;
    }

    private static String join(Set<String> values) {
        return values.isEmpty() ? null : String.join(",", values);
    }

    public MarketDataQualityStatus getQualityStatus() { return qualityStatus; }
    public String getSourceCode() { return sourceCode; }
    public LocalDateTime getAsOf() { return asOf; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public Long getStaleAgeSeconds() { return staleAgeSeconds; }
    public String getWarning() { return warning; }
    public String getRefreshId() { return refreshId; }
    public List<SectorMarketEntry> getItems() { return items; }
}
