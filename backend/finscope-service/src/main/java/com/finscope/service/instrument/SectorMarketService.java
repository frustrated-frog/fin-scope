package com.finscope.service.instrument;

import com.finscope.common.exception.BusinessException;
import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.instrument.SectorMarketSnapshot;
import com.finscope.service.marketdata.MarketDataGateway;
import com.finscope.service.marketdata.SectorCatalogGatewayResult;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.finscope.common.exception.BizErrorCode;

/** 板块目录排行与搜索服务；刷新、路由、缓存和兜底统一委托给市场数据网关。 */
@Service
public class SectorMarketService {
    private static final Comparator<SectorMarketEntry> LEADER_ORDER = Comparator
            .comparing(SectorMarketEntry::getMainNetInflow, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SectorMarketEntry::getSourceRank, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SectorMarketEntry::getCode);
    private static final Comparator<SectorMarketEntry> LAGGARD_ORDER = Comparator
            .comparing(SectorMarketEntry::getMainNetInflow, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SectorMarketEntry::getSourceRank, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SectorMarketEntry::getCode);

    @Resource
    private MarketDataGateway gateway;

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
                .filter(value -> value.getMainNetInflow() != null)
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
            throw new BusinessException(BizErrorCode.SECTOR_SEARCH_TERM_REQUIRED);
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
            if (result.getSnapshot() == null) {
                continue;
            }
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

    public Optional<SectorMarketEntry> findByCode(String code, boolean forceRefresh) {
        if (code == null || code.trim().isEmpty()) {
            return Optional.empty();
        }
        Map<String, SectorMarketEntry> values = findByCodes(
                Collections.singletonList(code.trim().toUpperCase(Locale.ROOT)), forceRefresh);
        return Optional.ofNullable(values.get(code.trim().toUpperCase(Locale.ROOT)));
    }

    public Map<String, SectorMarketEntry> findByCodes(List<String> codes, boolean forceRefresh) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> requested = codes.stream()
                .filter(value -> value != null && !value.trim().isEmpty())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, SectorMarketEntry> available = new LinkedHashMap<String, SectorMarketEntry>();
        for (SectorCategory category : Arrays.asList(SectorCategory.INDUSTRY, SectorCategory.CONCEPT)) {
            SectorCatalogGatewayResult result = gateway.fetchSectorCatalog(category, forceRefresh);
            if (result.getSnapshot() == null) {
                continue;
            }
            for (SectorMarketEntry entry : result.getSnapshot().getEntries()) {
                if (requested.contains(entry.getCode())) {
                    available.putIfAbsent(entry.getCode(), entry);
                }
            }
        }
        Map<String, SectorMarketEntry> ordered = new LinkedHashMap<String, SectorMarketEntry>();
        for (String code : requested) {
            if (available.containsKey(code)) {
                ordered.put(code, available.get(code));
            }
        }
        return ordered;
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
        if (code.equals(query) || name.equals(query)) {
            return 0;
        }
        if (code.startsWith(query) || name.startsWith(query)) {
            return 1;
        }
        return 2;
    }

    private void validateLimit(int limit, int maximum) {
        if (limit < 1 || limit > maximum) {
            throw new BusinessException(BizErrorCode.LIMIT_OUT_OF_RANGE, maximum);
        }
    }

    private void requireCategory(SectorCategory category) {
        if (category == null) {
            throw new BusinessException(BizErrorCode.SECTOR_CATEGORY_REQUIRED);
        }
    }

    private String mergeWarnings(String primary, List<String> secondary) {
        Set<String> values = new LinkedHashSet<String>();
        if (primary != null && !primary.trim().isEmpty()) {
            values.add(primary);
        }
        if (secondary != null) {
            for (String value : secondary) {
                if (value != null && !value.trim().isEmpty()
                        && (primary == null || !primary.contains(value))) {
                    values.add(value);
                }
            }
        }
        return values.isEmpty() ? null : String.join("；", values);
    }
}
