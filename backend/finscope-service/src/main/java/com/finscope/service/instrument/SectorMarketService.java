package com.finscope.service.instrument;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.instrument.SectorMarketSnapshot;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.quote.SectorMarketProvider;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/** 板块目录缓存、确定性排行与本地搜索服务。 */
@Service
public class SectorMarketService {
    private static final Duration FRESH_TTL = Duration.ofSeconds(30);
    private static final Duration STALE_TTL = Duration.ofMinutes(15);

    private static final Comparator<SectorMarketEntry> LEADER_ORDER = Comparator
            .comparing(SectorMarketEntry::getChangePct, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SectorMarketEntry::getTurnover, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SectorMarketEntry::getCode);
    private static final Comparator<SectorMarketEntry> LAGGARD_ORDER = Comparator
            .comparing(SectorMarketEntry::getChangePct, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SectorMarketEntry::getTurnover, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SectorMarketEntry::getCode);

    @Resource
    private List<SectorMarketProvider> providers;
    @Resource(name = "quoteTaskExecutor")
    private Executor executor;

    private final Clock clock;
    private final ConcurrentMap<SectorCategory, CacheState> cache = new ConcurrentHashMap<SectorCategory, CacheState>();
    private final ConcurrentMap<SectorCategory, CompletableFuture<SectorMarketSnapshot>> inFlight =
            new ConcurrentHashMap<SectorCategory, CompletableFuture<SectorMarketSnapshot>>();

    public SectorMarketService() {
        this(Clock.systemDefaultZone());
    }

    SectorMarketService(Clock clock) {
        this.clock = clock;
    }

    public SectorMarketOverview overview(SectorCategory category, int limit, boolean forceRefresh) {
        requireCategory(category);
        validateLimit(limit, 10);
        SnapshotResult snapshot = snapshot(category, forceRefresh);
        if (snapshot.snapshot == null) {
            return new SectorMarketOverview(category, SectorMarketQualityStatus.UNAVAILABLE, null,
                    snapshot.warning, Collections.<SectorMarketEntry>emptyList(), Collections.<SectorMarketEntry>emptyList());
        }
        List<SectorMarketEntry> valid = snapshot.snapshot.getEntries().stream()
                .filter(value -> value.getChangePct() != null)
                .collect(Collectors.toList());
        List<SectorMarketEntry> leaders = take(valid, LEADER_ORDER, limit, Collections.<String>emptySet());
        Set<String> leaderCodes = leaders.stream().map(SectorMarketEntry::getCode).collect(Collectors.toSet());
        List<SectorMarketEntry> laggards = take(valid, LAGGARD_ORDER, limit, leaderCodes);
        return new SectorMarketOverview(category, snapshot.qualityStatus, snapshot.snapshot.getRetrievedAt(),
                mergeWarnings(snapshot.warning, snapshot.snapshot.getWarnings()), leaders, laggards);
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
        List<String> warnings = new ArrayList<String>();
        LocalDateTime retrievedAt = null;
        boolean degraded = false;
        boolean available = false;
        for (SectorCategory value : categories) {
            SnapshotResult result = snapshot(value, false);
            if (result.warning != null) warnings.add(result.warning);
            if (result.snapshot == null) {
                degraded = true;
                continue;
            }
            available = true;
            degraded = degraded || result.qualityStatus != SectorMarketQualityStatus.FRESH;
            if (retrievedAt == null || result.snapshot.getRetrievedAt().isBefore(retrievedAt)) {
                retrievedAt = result.snapshot.getRetrievedAt();
            }
            for (SectorMarketEntry entry : result.snapshot.getEntries()) unique.putIfAbsent(entry.getCode(), entry);
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        List<SectorMarketEntry> matched = unique.values().stream()
                .filter(value -> matches(value, upper))
                .sorted(searchOrder(upper))
                .limit(limit)
                .collect(Collectors.toList());
        SectorMarketQualityStatus quality = !available ? SectorMarketQualityStatus.UNAVAILABLE
                : degraded ? SectorMarketQualityStatus.STALE : SectorMarketQualityStatus.FRESH;
        return new SectorMarketSearchResult(quality, retrievedAt, join(warnings), matched);
    }

    private SnapshotResult snapshot(SectorCategory category, boolean forceRefresh) {
        CacheState current = cache.get(category);
        Instant now = clock.instant();
        if (!forceRefresh && current != null && Duration.between(current.cachedAt, now).compareTo(FRESH_TTL) <= 0) {
            return SnapshotResult.available(current.snapshot, SectorMarketQualityStatus.FRESH, null);
        }
        try {
            SectorMarketSnapshot refreshed = refresh(category).join();
            return SnapshotResult.available(refreshed, SectorMarketQualityStatus.FRESH, null);
        } catch (CompletionException error) {
            Throwable cause = unwrap(error);
            current = cache.get(category);
            String warning = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            if (current != null && Duration.between(current.cachedAt, now).compareTo(STALE_TTL) <= 0) {
                return SnapshotResult.available(current.snapshot, SectorMarketQualityStatus.STALE, warning);
            }
            return SnapshotResult.unavailable(warning);
        }
    }

    private CompletableFuture<SectorMarketSnapshot> refresh(SectorCategory category) {
        while (true) {
            CompletableFuture<SectorMarketSnapshot> existing = inFlight.get(category);
            if (existing != null) return existing;
            CompletableFuture<SectorMarketSnapshot> created = new CompletableFuture<SectorMarketSnapshot>();
            if (inFlight.putIfAbsent(category, created) != null) continue;
            executor.execute(() -> {
                try {
                    SectorMarketSnapshot value = provider(category).fetch(category);
                    if (value == null || value.getEntries().isEmpty()) {
                        throw new ProviderContractException("EMPTY_SECTOR_CATALOG", "板块目录为空", true);
                    }
                    cache.put(category, new CacheState(value, clock.instant()));
                    created.complete(value);
                } catch (Throwable error) {
                    created.completeExceptionally(error);
                } finally {
                    inFlight.remove(category, created);
                }
            });
            return created;
        }
    }

    private SectorMarketProvider provider(SectorCategory category) {
        if (providers != null) {
            for (SectorMarketProvider provider : providers) if (provider.supports(category)) return provider;
        }
        throw new ProviderContractException("SECTOR_PROVIDER_MISSING", "没有可用的板块目录 Provider", false);
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

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException) && current.getCause() != null) current = current.getCause();
        return current;
    }

    private String mergeWarnings(String primary, List<String> secondary) {
        List<String> values = new ArrayList<String>();
        if (primary != null && !primary.trim().isEmpty()) values.add(primary);
        if (secondary != null) values.addAll(secondary);
        return join(values);
    }

    private String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join("；", values);
    }

    private static final class CacheState {
        private final SectorMarketSnapshot snapshot;
        private final Instant cachedAt;
        private CacheState(SectorMarketSnapshot snapshot, Instant cachedAt) {
            this.snapshot = snapshot;
            this.cachedAt = cachedAt;
        }
    }

    private static final class SnapshotResult {
        private final SectorMarketSnapshot snapshot;
        private final SectorMarketQualityStatus qualityStatus;
        private final String warning;

        private SnapshotResult(SectorMarketSnapshot snapshot, SectorMarketQualityStatus qualityStatus, String warning) {
            this.snapshot = snapshot;
            this.qualityStatus = qualityStatus;
            this.warning = warning;
        }

        private static SnapshotResult available(SectorMarketSnapshot snapshot,
                                                SectorMarketQualityStatus qualityStatus, String warning) {
            return new SnapshotResult(snapshot, qualityStatus, warning);
        }

        private static SnapshotResult unavailable(String warning) {
            return new SnapshotResult(null, SectorMarketQualityStatus.UNAVAILABLE, warning);
        }
    }
}
