package com.finscope.service.instrument;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.instrument.SectorMarketSnapshot;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.service.marketdata.MarketDataGateway;
import com.finscope.service.marketdata.SectorCatalogGatewayResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 板块目录排行与搜索服务；刷新、路由、缓存和兜底统一委托给市场数据网关。 */
@Service
public class SectorMarketService {
    private static final Comparator<SectorMarketEntry> LEADER_ORDER = Comparator
            .comparing(SectorMarketEntry::getChangePct, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SectorMarketEntry::getTurnover, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SectorMarketEntry::getCode);
    private static final Comparator<SectorMarketEntry> LAGGARD_ORDER = Comparator
            .comparing(SectorMarketEntry::getChangePct, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SectorMarketEntry::getTurnover, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SectorMarketEntry::getCode);

    private final MarketDataGateway gateway;

    public SectorMarketService(MarketDataGateway gateway) {
        this.gateway = gateway;
    }

    public SectorMarketOverview overview(SectorCategory category, int limit, boolean forceRefresh) {
        requireCategory(category);
        validateLimit(limit, 10);
        SectorCatalogGatewayResult result = gateway.fetchSectorCatalog(category, forceRefresh);
        SectorMarketSnapshot snapshot = result.getSnapshot();
        if (snapshot == null) {
            return SectorMarketOverview.of(category, result, Collections.<SectorMarketEntry>emptyList(),
                    Collections.<SectorMarketEntry>emptyList(), result.getWarning());
        }
        List<SectorMarketEntry> valid = snapshot.getEntries().stream()
                .filter(value -> value.getChangePct() != null)
                .collect(Collectors.toList());
        List<SectorMarketEntry> leaders = take(valid, LEADER_ORDER, limit, Collections.<String>emptySet());
        Set<String> leaderCodes = leaders.stream().map(SectorMarketEntry::getCode).collect(Collectors.toSet());
        List<SectorMarketEntry> laggards = take(valid, LAGGARD_ORDER, limit, leaderCodes);
        return SectorMarketOverview.of(category, result, leaders, laggards,
                mergeWarnings(result.getWarning(), snapshot.getWarnings()));
    }

    public SectorMarketSearchResult search(String query, SectorCategory category, int limit) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "板块搜索词不能为空");
        }
        validateLimit(limit, 20);
        List<SectorCategory> categories = category == null
                ? Arrays.asList(SectorCategory.INDUSTRY, SectorCategory.CONCEPT)
                : Collections.singletonList(category);
        Map<String, SectorMarketEntry> unique = new LinkedHashMap<String, SectorMarketEntry>();
        List<SectorCatalogGatewayResult> results = new ArrayList<SectorCatalogGatewayResult>();
        for (SectorCategory value : categories) {
            SectorCatalogGatewayResult result = gateway.fetchSectorCatalog(value, false);
            results.add(result);
            if (result.getSnapshot() == null) continue;
            for (SectorMarketEntry entry : result.getSnapshot().getEntries()) {
                unique.putIfAbsent(entry.getCode(), entry);
            }
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        List<SectorMarketEntry> matched = unique.values().stream()
                .filter(value -> matches(value, upper))
                .sorted(searchOrder(upper))
                .limit(limit)
                .collect(Collectors.toList());
        return SectorMarketSearchResult.of(results, matched);
    }

    private List<SectorMarketEntry> take(List<SectorMarketEntry> values, Comparator<SectorMarketEntry> order,
                                         int limit, Set<String> excludedCodes) {
        return values.stream().filter(value -> !excludedCodes.contains(value.getCode())).sorted(order).limit(limit)
                .collect(Collectors.toList());
    }

    private boolean matches(SectorMarketEntry entry, String query) {
        return entry.getCode().toUpperCase(Locale.ROOT).contains(query)
                || entry.getName().toUpperCase(Locale.ROOT).contains(query);
    }

    private Comparator<SectorMarketEntry> searchOrder(String query) {
        return Comparator.comparingInt((SectorMarketEntry value) -> matchRank(value, query))
                .thenComparing(SectorMarketEntry::getChangePct,
                        Comparator.nullsLast(Comparator.comparingDouble((Double value) -> Math.abs(value)).reversed()))
                .thenComparing(SectorMarketEntry::getTurnover, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SectorMarketEntry::getCode);
    }

    private int matchRank(SectorMarketEntry entry, String query) {
        String code = entry.getCode().toUpperCase(Locale.ROOT);
        String name = entry.getName().toUpperCase(Locale.ROOT);
        if (code.equals(query) || name.equals(query)) return 0;
        if (code.startsWith(query) || name.startsWith(query)) return 1;
        return 2;
    }

    private void validateLimit(int limit, int maximum) {
        if (limit < 1 || limit > maximum) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "limit 必须在 1 到 " + maximum + " 之间");
        }
    }

    private void requireCategory(SectorCategory category) {
        if (category == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "板块分类不能为空");
    }

    private String mergeWarnings(String primary, List<String> secondary) {
        Set<String> values = new LinkedHashSet<String>();
        if (primary != null && !primary.trim().isEmpty()) values.add(primary);
        if (secondary != null) {
            for (String value : secondary) if (value != null && !value.trim().isEmpty()) values.add(value);
        }
        return values.isEmpty() ? null : String.join("；", values);
    }
}
