package com.finscope.service.marketdata;

import com.finscope.dao.marketdata.MarketDataRefreshRunRepository;
import com.finscope.dao.marketdata.MarketDataSnapshotRepository;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.instrument.SectorMarketSnapshot;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.domain.marketdata.MarketDataSnapshot;
import com.finscope.domain.marketintel.DragonTigerRecord;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import com.finscope.rpc.marketintel.CapitalFlowData;
import com.finscope.rpc.marketintel.CapitalFlowProvider;
import com.finscope.rpc.marketintel.DragonTigerData;
import com.finscope.rpc.marketintel.DragonTigerProvider;
import com.finscope.rpc.quote.QuoteAdapter;
import com.finscope.rpc.quote.SectorMarketProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 现有页面访问外部行情的统一高可用入口。 */
@Service
public class MarketDataGateway {
    private static final Logger log = LoggerFactory.getLogger(MarketDataGateway.class);

    private final List<QuoteAdapter> quoteAdapters;
    private final List<SectorMarketProvider> sectorProviders;
    private final List<CapitalFlowProvider> capitalFlowProviders;
    private final List<DragonTigerProvider> dragonTigerProviders;
    private final ProviderRoutePolicy routePolicy;
    private final ProviderRequestGuard guard;
    private final MarketDataSnapshotRepository snapshots;
    private final MarketDataRefreshRunRepository refreshRuns;
    private final MarketDataSnapshotCodec codec;
    private final QuoteQualityValidator validator;
    private final MarketDataSingleFlight singleFlight;
    private final MarketDataGatewayProperties properties;
    private final Executor executor;
    private final Clock clock;
    private final Map<String, CacheEntry> freshCache =
            new java.util.concurrent.ConcurrentHashMap<String, CacheEntry>();
    private final Map<String, SectorCacheEntry> sectorFreshCache =
            new java.util.concurrent.ConcurrentHashMap<String, SectorCacheEntry>();

    @Autowired
    public MarketDataGateway(List<QuoteAdapter> quoteAdapters,
                             List<SectorMarketProvider> sectorProviders,
                             List<CapitalFlowProvider> capitalFlowProviders,
                             List<DragonTigerProvider> dragonTigerProviders,
                             ProviderRoutePolicy routePolicy,
                             ProviderRequestGuard guard,
                             MarketDataSnapshotRepository snapshots,
                             MarketDataRefreshRunRepository refreshRuns,
                             MarketDataSnapshotCodec codec,
                             QuoteQualityValidator validator,
                             MarketDataSingleFlight singleFlight,
                             MarketDataGatewayProperties properties,
                             @Qualifier("marketDataGatewayExecutor") Executor executor) {
        this(quoteAdapters, sectorProviders, capitalFlowProviders, dragonTigerProviders,
                routePolicy, guard, snapshots,
                refreshRuns, codec, validator,
                singleFlight, properties, executor, Clock.systemDefaultZone());
    }

    /** 保留给聚焦行情测试和非 Spring 调用方的兼容构造器。 */
    public MarketDataGateway(List<QuoteAdapter> quoteAdapters,
                             ProviderRoutePolicy routePolicy,
                             ProviderRequestGuard guard,
                             MarketDataSnapshotRepository snapshots,
                             MarketDataRefreshRunRepository refreshRuns,
                             MarketDataSnapshotCodec codec,
                             QuoteQualityValidator validator,
                             MarketDataSingleFlight singleFlight,
                             MarketDataGatewayProperties properties,
                             Executor executor,
                             Clock clock) {
        this(quoteAdapters, Collections.<SectorMarketProvider>emptyList(), routePolicy, guard, snapshots,
                refreshRuns, codec, validator, singleFlight, properties, executor, clock);
    }

    public MarketDataGateway(List<QuoteAdapter> quoteAdapters,
                             List<SectorMarketProvider> sectorProviders,
                             ProviderRoutePolicy routePolicy,
                             ProviderRequestGuard guard,
                             MarketDataSnapshotRepository snapshots,
                             MarketDataRefreshRunRepository refreshRuns,
                             MarketDataSnapshotCodec codec,
                             QuoteQualityValidator validator,
                             MarketDataSingleFlight singleFlight,
                             MarketDataGatewayProperties properties,
                             Executor executor,
                             Clock clock) {
        this(quoteAdapters, sectorProviders, Collections.<CapitalFlowProvider>emptyList(), routePolicy,
                guard, snapshots, refreshRuns, codec, validator, singleFlight, properties, executor, clock);
    }

    public MarketDataGateway(List<QuoteAdapter> quoteAdapters,
                             List<SectorMarketProvider> sectorProviders,
                             List<CapitalFlowProvider> capitalFlowProviders,
                             ProviderRoutePolicy routePolicy,
                             ProviderRequestGuard guard,
                             MarketDataSnapshotRepository snapshots,
                             MarketDataRefreshRunRepository refreshRuns,
                             MarketDataSnapshotCodec codec,
                             QuoteQualityValidator validator,
                             MarketDataSingleFlight singleFlight,
                             MarketDataGatewayProperties properties,
                             Executor executor,
                             Clock clock) {
        this(quoteAdapters, sectorProviders, capitalFlowProviders,
                Collections.<DragonTigerProvider>emptyList(), routePolicy, guard, snapshots,
                refreshRuns, codec, validator, singleFlight, properties, executor, clock);
    }

    public MarketDataGateway(List<QuoteAdapter> quoteAdapters,
                             List<SectorMarketProvider> sectorProviders,
                             List<CapitalFlowProvider> capitalFlowProviders,
                             List<DragonTigerProvider> dragonTigerProviders,
                             ProviderRoutePolicy routePolicy,
                             ProviderRequestGuard guard,
                             MarketDataSnapshotRepository snapshots,
                             MarketDataRefreshRunRepository refreshRuns,
                             MarketDataSnapshotCodec codec,
                             QuoteQualityValidator validator,
                             MarketDataSingleFlight singleFlight,
                             MarketDataGatewayProperties properties,
                             Executor executor,
                             Clock clock) {
        this.quoteAdapters = new ArrayList<QuoteAdapter>(quoteAdapters);
        this.sectorProviders = new ArrayList<SectorMarketProvider>(sectorProviders);
        this.capitalFlowProviders = new ArrayList<CapitalFlowProvider>(capitalFlowProviders);
        this.dragonTigerProviders = new ArrayList<DragonTigerProvider>(dragonTigerProviders);
        this.routePolicy = routePolicy;
        this.guard = guard;
        this.snapshots = snapshots;
        this.refreshRuns = refreshRuns;
        this.codec = codec;
        this.validator = validator;
        this.singleFlight = singleFlight;
        this.properties = properties;
        this.executor = executor;
        this.clock = clock;
    }

    public DragonTigerGatewayResult fetchDragonTiger(
            Instrument instrument, LocalDate startDate, LocalDate endDate) {
        if (instrument == null || instrument.getId() == null) {
            throw new IllegalArgumentException("instrument is required");
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("dragon tiger date range is invalid");
        }
        String flightKey = dragonTigerScopeKey(instrument.getId(), startDate, endDate);
        return singleFlight.execute(flightKey,
                () -> routeDragonTiger(instrument, startDate, endDate, flightKey));
    }

    private DragonTigerGatewayResult routeDragonTiger(
            Instrument instrument, LocalDate startDate, LocalDate endDate, String scopeKey) {
        MarketDataCapability capability = MarketDataCapability.DRAGON_TIGER;
        String refreshId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now(clock);
        Long runId = createAudit(capability,
                instrument.getId() + ":" + startDate + ":" + endDate, startedAt);
        Optional<MarketDataSnapshot> stored = findSnapshot(capability, scopeKey);
        Optional<DragonTigerData> lastGood = stored.flatMap(codec::decodeDragonTiger);
        List<DragonTigerProvider> candidates = new ArrayList<DragonTigerProvider>();
        for (DragonTigerProvider provider : dragonTigerProviders) {
            if (provider.supports(instrument)) {
                candidates.add(provider);
            }
        }
        List<DragonTigerProvider> ordered = routePolicy.order(candidates, capability);
        String primaryCode = ordered.isEmpty() ? null : ordered.get(0).providerCode();
        List<String> failures = new ArrayList<String>();
        Set<String> attemptedSources = new LinkedHashSet<String>();
        String lastErrorType = MarketDataQualityStatus.UNAVAILABLE.name();

        for (DragonTigerProvider provider : ordered) {
            attemptedSources.add(provider.providerCode());
            try {
                ProviderResult<DragonTigerData> fetched = guard.execute(provider, capability,
                        () -> provider.fetch(instrument, startDate, endDate));
                if (fetched == null || fetched.getData() == null) {
                    throw new com.finscope.rpc.marketintel.ProviderContractException(
                            "EMPTY_DRAGON_TIGER_RESULT", "龙虎榜 Provider 返回空对象", true);
                }
                DragonTigerData data = fetched.getData();
                LocalDateTime dataAsOf = dragonTigerDataAsOf(data, endDate);
                String warning = joinWarnings(fetched.getWarnings());
                try {
                    snapshots.upsert(codec.dragonTigerSnapshot(scopeKey, provider.providerCode(),
                            provider.providerFamily(), data, dataAsOf, fetched.getRetrievedAt(),
                            LocalDateTime.now(clock)));
                } catch (RuntimeException persistenceError) {
                    warning = appendWarning(warning, "本地龙虎榜兜底快照保存失败");
                    log.warn("Failed to persist dragon tiger snapshot for {}", scopeKey, persistenceError);
                }
                boolean fallback = primaryCode != null && !primaryCode.equals(provider.providerCode());
                MarketDataQualityStatus status = fallback
                        ? MarketDataQualityStatus.FRESH_FALLBACK
                        : MarketDataQualityStatus.FRESH_PRIMARY;
                if (fallback) {
                    warning = appendWarning("主龙虎榜数据源不可用，系统已自动切换备用数据源。", warning);
                }
                DragonTigerGatewayResult result = new DragonTigerGatewayResult(
                        data, status, provider.providerCode(), dataAsOf, fetched.getRetrievedAt(),
                        null, warning, null, refreshId);
                finishDragonTigerAudit(runId, result, attemptedSources, data.getRecords().size());
                return result;
            } catch (RuntimeException error) {
                if (error instanceof com.finscope.rpc.marketintel.ProviderContractException) {
                    lastErrorType = ((com.finscope.rpc.marketintel.ProviderContractException) error)
                            .getErrorType();
                }
                failures.add(provider.providerCode() + "：" + message(error));
            }
        }

        if (lastGood.isPresent() && stored.isPresent()) {
            MarketDataSnapshot snapshot = stored.get();
            long age = Math.max(0L, Duration.between(
                    snapshot.getRetrievedAt(), LocalDateTime.now(clock)).getSeconds());
            String reason = failures.isEmpty() ? "没有健康且支持该标的的数据源"
                    : String.join("；", failures);
            DragonTigerGatewayResult result = new DragonTigerGatewayResult(
                    lastGood.get(), MarketDataQualityStatus.STALE_FALLBACK,
                    snapshot.getProviderCode(), snapshot.getAsOf(), snapshot.getRetrievedAt(),
                    age, "龙虎榜在线刷新失败，正在显示最近一次成功数据（已过期 "
                    + age + " 秒）。原因：" + reason, lastErrorType, refreshId);
            finishDragonTigerAudit(runId, result, attemptedSources,
                    lastGood.get().getRecords().size());
            return result;
        }

        String source = attemptedSources.isEmpty() ? null : String.join(",", attemptedSources);
        String reason = failures.isEmpty() ? "没有可用的龙虎榜数据源" : String.join("；", failures);
        DragonTigerGatewayResult result = new DragonTigerGatewayResult(
                null, MarketDataQualityStatus.UNAVAILABLE, source, null, startedAt,
                null, "龙虎榜刷新失败，且没有可用的历史快照。原因：" + reason,
                lastErrorType, refreshId);
        finishDragonTigerAudit(runId, result, attemptedSources, 0);
        return result;
    }

    private String dragonTigerScopeKey(Long instrumentId, LocalDate startDate, LocalDate endDate) {
        long windowDays = ChronoUnit.DAYS.between(startDate, endDate) + 1L;
        return MarketDataCapability.DRAGON_TIGER.name() + ":" + instrumentId
                + ":" + windowDays + "D";
    }

    private LocalDateTime dragonTigerDataAsOf(DragonTigerData data, LocalDate endDate) {
        LocalDate latest = null;
        for (DragonTigerRecord record : data.getRecords()) {
            if (record.getTradeDate() != null
                    && (latest == null || record.getTradeDate().isAfter(latest))) {
                latest = record.getTradeDate();
            }
        }
        return (latest == null ? endDate : latest).atTime(15, 30);
    }

    private void finishDragonTigerAudit(
            Long runId, DragonTigerGatewayResult result,
            Set<String> attemptedSources, int outputCount) {
        if (runId == null) {
            return;
        }
        boolean fresh = result.getQualityStatus() == MarketDataQualityStatus.FRESH_PRIMARY
                || result.getQualityStatus() == MarketDataQualityStatus.FRESH_FALLBACK;
        boolean stale = result.getQualityStatus() == MarketDataQualityStatus.STALE_FALLBACK;
        try {
            refreshRuns.finish(runId, result.getQualityStatus().name(), 1,
                    fresh ? 1 : 0, stale ? 1 : 0,
                    result.getQualityStatus() == MarketDataQualityStatus.UNAVAILABLE ? 1 : 0,
                    String.join(",", attemptedSources),
                    appendWarning(result.getWarning(), "龙虎榜记录数：" + outputCount),
                    LocalDateTime.now(clock));
        } catch (RuntimeException error) {
            log.warn("Failed to finish dragon tiger refresh audit {}", runId, error);
        }
    }

    public CapitalFlowGatewayResult fetchCapitalFlow(Instrument instrument, LocalDate asOfDate) {
        if (instrument == null || instrument.getId() == null) {
            throw new IllegalArgumentException("instrument is required");
        }
        if (asOfDate == null) throw new IllegalArgumentException("capital flow date is required");
        String flightKey = MarketDataCapability.CAPITAL_FLOW_5M.name() + ":" + instrument.getId()
                + ":" + asOfDate;
        return singleFlight.execute(flightKey, () -> routeCapitalFlow(instrument, asOfDate));
    }

    private CapitalFlowGatewayResult routeCapitalFlow(Instrument instrument, LocalDate asOfDate) {
        MarketDataCapability capability = MarketDataCapability.CAPITAL_FLOW_5M;
        String refreshId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now(clock);
        Long runId = createAudit(capability, instrument.getId() + ":" + asOfDate, startedAt);
        List<CapitalFlowProvider> candidates = new ArrayList<CapitalFlowProvider>();
        for (CapitalFlowProvider provider : capitalFlowProviders) {
            if (provider.supports(instrument)) candidates.add(provider);
        }
        List<CapitalFlowProvider> ordered = routePolicy.order(candidates, capability);
        String primaryCode = ordered.isEmpty() ? null : ordered.get(0).providerCode();
        List<String> attempts = new ArrayList<String>();
        String lastErrorType = MarketDataQualityStatus.UNAVAILABLE.name();
        String lastErrorMessage = null;
        Set<String> attemptedSources = new LinkedHashSet<String>();
        for (CapitalFlowProvider provider : ordered) {
            attemptedSources.add(provider.providerCode());
            try {
                ProviderResult<CapitalFlowData> fetched = guard.execute(provider, capability, () -> {
                    CapitalFlowData data = provider.fetch(instrument, asOfDate);
                    if (data == null || data.getMinutePoints().isEmpty()) {
                        throw new com.finscope.rpc.marketintel.ProviderContractException(
                                "EMPTY_CAPITAL_FLOW", "资金流数据缺少 5 分钟明细", true);
                    }
                    return ProviderResult.of(data, LocalDateTime.now(clock),
                            ProviderResult.hashOf(data.allPoints()), data.getWarnings());
                });
                boolean fallback = primaryCode != null && !primaryCode.equals(provider.providerCode());
                MarketDataQualityStatus status = fallback
                        ? MarketDataQualityStatus.FRESH_FALLBACK : MarketDataQualityStatus.FRESH_PRIMARY;
                String warning = joinWarnings(fetched.getWarnings());
                if (fallback) warning = appendWarning(
                        "主资金流数据源不可用，系统已自动切换备用数据源。", warning);
                CapitalFlowGatewayResult result = new CapitalFlowGatewayResult(fetched.getData(), status,
                        provider.providerCode(), fetched.getRetrievedAt(), warning, null, refreshId);
                finishCapitalAudit(runId, result, attemptedSources, fetched.getData().allPoints().size());
                return result;
            } catch (RuntimeException error) {
                lastErrorMessage = message(error);
                if (error instanceof com.finscope.rpc.marketintel.ProviderContractException) {
                    lastErrorType = ((com.finscope.rpc.marketintel.ProviderContractException) error).getErrorType();
                }
                attempts.add(provider.providerCode() + "：" + lastErrorMessage);
            }
        }
        String source = attemptedSources.isEmpty() ? null : String.join(",", attemptedSources);
        String reason = attempts.isEmpty() ? "没有健康且支持该标的的数据源" : String.join("；", attempts);
        String warning = attempts.size() == 1 ? lastErrorMessage
                : "资金流在线数据源均不可用；如存在历史快照，系统将保留旧事实。原因：" + reason;
        CapitalFlowGatewayResult result = new CapitalFlowGatewayResult(null,
                MarketDataQualityStatus.UNAVAILABLE, source, startedAt,
                warning, lastErrorType, refreshId);
        finishCapitalAudit(runId, result, attemptedSources, 0);
        return result;
    }

    private void finishCapitalAudit(Long runId, CapitalFlowGatewayResult result,
                                    Set<String> selectedSources, int outputCount) {
        if (runId == null) return;
        boolean fresh = result.getData() != null;
        try {
            refreshRuns.finish(runId, result.getQualityStatus().name(), 1, fresh ? 1 : 0,
                    0, fresh ? 0 : 1, String.join(",", selectedSources),
                    appendWarning(result.getWarning(), "资金流条目数：" + outputCount), LocalDateTime.now(clock));
        } catch (RuntimeException error) {
            log.warn("Failed to finish capital flow refresh audit {}", runId, error);
        }
    }

    public SectorCatalogGatewayResult fetchSectorCatalog(SectorCategory category, boolean forceRefresh) {
        if (category == null) throw new IllegalArgumentException("sector category is required");
        String key = sectorScopeKey(category);
        if (!forceRefresh) {
            SectorCacheEntry cached = sectorFreshCache.get(key);
            if (cached != null && clock.millis() - cached.createdAtMillis <= properties.getFreshCacheMs()) {
                return cached.result;
            }
        }
        return singleFlight.execute(key, () -> {
            SectorCatalogGatewayResult result = routeSectorCatalog(category);
            if (result.getQualityStatus() != MarketDataQualityStatus.UNAVAILABLE) {
                sectorFreshCache.put(key, new SectorCacheEntry(result, clock.millis()));
            }
            return result;
        });
    }

    private SectorCatalogGatewayResult routeSectorCatalog(SectorCategory category) {
        MarketDataCapability capability = MarketDataCapability.SECTOR_CATALOG;
        String scopeKey = sectorScopeKey(category);
        String refreshId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now(clock);
        Long runId = createAudit(capability, category.name(), startedAt);
        Optional<MarketDataSnapshot> stored = findSnapshot(capability, scopeKey);
        Optional<SectorMarketSnapshot> lastGood = stored.flatMap(codec::decodeSectorCatalog);
        List<SectorMarketProvider> candidates = new ArrayList<SectorMarketProvider>();
        for (SectorMarketProvider provider : sectorProviders) {
            if (provider.supports(category)) candidates.add(provider);
        }
        List<SectorMarketProvider> ordered = routePolicy.order(candidates, capability);
        String primaryCode = ordered.isEmpty() ? null : ordered.get(0).providerCode();
        List<String> failures = new ArrayList<String>();

        for (SectorMarketProvider provider : ordered) {
            try {
                ProviderResult<SectorMarketSnapshot> fetched = guard.execute(provider, capability,
                        () -> validateSectorCatalog(provider.fetchResult(category), category, lastGood));
                SectorMarketSnapshot fresh = fetched.getData();
                boolean fallback = primaryCode != null && !primaryCode.equals(provider.providerCode());
                MarketDataQualityStatus status = fallback
                        ? MarketDataQualityStatus.FRESH_FALLBACK : MarketDataQualityStatus.FRESH_PRIMARY;
                String warning = joinWarnings(fetched.getWarnings());
                try {
                    snapshots.upsert(codec.sectorCatalogSnapshot(scopeKey, provider.providerCode(),
                            provider.providerFamily(), fresh, LocalDateTime.now(clock)));
                } catch (RuntimeException persistenceError) {
                    warning = appendWarning(warning, "本地板块目录兜底快照保存失败");
                    log.warn("Failed to persist sector catalog snapshot for {}", category, persistenceError);
                }
                if (fallback) warning = appendWarning(
                        "主数据源响应失败或目录不完整，系统已自动切换备用数据源。", warning);
                SectorCatalogGatewayResult result = new SectorCatalogGatewayResult(fresh, status,
                        provider.providerCode(), fresh.getRetrievedAt(), fetched.getRetrievedAt(),
                        null, warning, refreshId);
                finishSectorAudit(runId, result, provider.providerCode(), fresh.getEntries().size());
                return result;
            } catch (RuntimeException error) {
                failures.add(message(error));
            }
        }

        if (lastGood.isPresent() && stored.isPresent()) {
            SectorMarketSnapshot stale = lastGood.get();
            long age = Math.max(0L, Duration.between(stored.get().getRetrievedAt(),
                    LocalDateTime.now(clock)).getSeconds());
            String reason = failures.isEmpty() ? "没有健康的数据源" : String.join("；", failures);
            String warning = "板块目录刷新失败，正在显示最近一次成功数据（已过期 " + age
                    + " 秒）。原因：" + reason;
            SectorCatalogGatewayResult result = new SectorCatalogGatewayResult(stale,
                    MarketDataQualityStatus.STALE_FALLBACK, stored.get().getProviderCode(),
                    stale.getRetrievedAt(), stored.get().getRetrievedAt(), age, warning, refreshId);
            finishSectorAudit(runId, result, stored.get().getProviderCode(), stale.getEntries().size());
            return result;
        }

        String reason = failures.isEmpty() ? "没有可用的板块目录数据源" : String.join("；", failures);
        SectorCatalogGatewayResult result = new SectorCatalogGatewayResult(null,
                MarketDataQualityStatus.UNAVAILABLE, null, null, startedAt, null,
                "板块目录刷新失败，且没有可用的历史快照。原因：" + reason, refreshId);
        finishSectorAudit(runId, result, null, 0);
        return result;
    }

    private ProviderResult<SectorMarketSnapshot> validateSectorCatalog(
            ProviderResult<SectorMarketSnapshot> result, SectorCategory category,
            Optional<SectorMarketSnapshot> lastGood) {
        if (result == null || result.getData() == null) {
            throw new com.finscope.rpc.marketintel.ProviderContractException(
                    "EMPTY_SECTOR_CATALOG", "板块目录为空", true);
        }
        List<SectorMarketEntry> valid = new ArrayList<SectorMarketEntry>();
        for (SectorMarketEntry entry : result.getData().getEntries()) {
            if (entry != null && entry.getCode() != null && entry.getCode().matches("BK\\d{4}")
                    && entry.getName() != null && !entry.getName().trim().isEmpty()
                    && (entry.getCategory() == null || entry.getCategory() == category)) {
                if (entry.getCategory() == null) entry.setCategory(category);
                valid.add(entry);
            }
        }
        if (valid.isEmpty()) {
            throw new com.finscope.rpc.marketintel.ProviderContractException(
                    "EMPTY_SECTOR_CATALOG", "板块目录没有有效条目", true);
        }
        int previousCount = lastGood.map(value -> value.getEntries().size()).orElse(0);
        int minimumAccepted = (int) Math.ceil(previousCount * 0.70d);
        if (previousCount > 0 && valid.size() < minimumAccepted) {
            throw new com.finscope.rpc.marketintel.ProviderContractException(
                    "SUSPICIOUS_SECTOR_COVERAGE",
                    "板块目录数量异常下降：本次 " + valid.size() + " 条，上次 " + previousCount + " 条",
                    true);
        }
        SectorMarketSnapshot original = result.getData();
        SectorMarketSnapshot normalized = new SectorMarketSnapshot(category, original.getProviderCode(),
                original.getRetrievedAt(), original.getPayloadFingerprint(), valid, original.getWarnings());
        return ProviderResult.of(normalized, result.getRetrievedAt(), result.getPayloadHash(),
                result.getWarnings());
    }

    private String sectorScopeKey(SectorCategory category) {
        return MarketDataCapability.SECTOR_CATALOG.name() + ":" + category.name();
    }

    private void finishSectorAudit(Long runId, SectorCatalogGatewayResult result,
                                   String selectedSource, int itemCount) {
        if (runId == null) return;
        boolean fresh = result.getQualityStatus() == MarketDataQualityStatus.FRESH_PRIMARY
                || result.getQualityStatus() == MarketDataQualityStatus.FRESH_FALLBACK;
        boolean stale = result.getQualityStatus() == MarketDataQualityStatus.STALE_FALLBACK;
        try {
            refreshRuns.finish(runId, result.getQualityStatus().name(), 1, fresh ? 1 : 0,
                    stale ? 1 : 0, result.getQualityStatus() == MarketDataQualityStatus.UNAVAILABLE ? 1 : 0,
                    selectedSource, appendWarning(result.getWarning(), "目录条目数：" + itemCount),
                    LocalDateTime.now(clock));
        } catch (RuntimeException error) {
            log.warn("Failed to finish sector catalog refresh audit {}", runId, error);
        }
    }

    private String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private String joinWarnings(List<String> warnings) {
        return warnings == null || warnings.isEmpty() ? null : String.join("；", warnings);
    }

    private String appendWarning(String first, String second) {
        if (first == null || first.trim().isEmpty()) return second;
        if (second == null || second.trim().isEmpty()) return first;
        return first + " " + second;
    }

    public QuoteGatewayResult fetchQuotes(String type, List<String> codes, boolean forceRefresh) {
        String normalizedType = normalizeType(type);
        MarketDataCapability capability = quoteCapability(normalizedType);
        List<String> normalizedCodes = normalizeCodes(codes);
        String flightKey = capability.name() + ":" + String.join(",", normalizedCodes);
        if (!forceRefresh) {
            CacheEntry cached = freshCache.get(flightKey);
            if (cached != null && clock.millis() - cached.createdAtMillis <= properties.getFreshCacheMs()) {
                return cached.result;
            }
        }
        return singleFlight.execute(flightKey, () -> {
            QuoteGatewayResult result = routeQuotes(capability, normalizedType, normalizedCodes);
            if (result.getQualityStatus() != MarketDataQualityStatus.UNAVAILABLE) {
                freshCache.put(flightKey, new CacheEntry(result, clock.millis()));
            }
            return result;
        });
    }

    private QuoteGatewayResult routeQuotes(MarketDataCapability capability, String type,
                                           List<String> codes) {
        String refreshId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now(clock);
        Long runId = createAudit(capability, type + ":" + codes.size(), startedAt);
        List<QuoteAdapter> candidates = new ArrayList<QuoteAdapter>();
        for (QuoteAdapter adapter : quoteAdapters) {
            if (adapter.supports(type)) candidates.add(adapter);
        }
        List<QuoteAdapter> ordered = routePolicy.order(candidates, capability);
        List<ProviderAttempt> attempts = fetchWithHedge(ordered, capability, codes);
        QuoteGatewayResult result = composeResult(
                capability, type, codes, ordered, attempts, refreshId, startedAt);
        finishAudit(runId, result, attempts, codes.size());
        return result;
    }

    private List<ProviderAttempt> fetchWithHedge(List<QuoteAdapter> ordered,
                                                 MarketDataCapability capability,
                                                 List<String> codes) {
        if (ordered.isEmpty() || codes.isEmpty()) return Collections.emptyList();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(properties.getRequestBudgetMs());
        List<ProviderAttempt> completed = new ArrayList<ProviderAttempt>();
        List<PendingAttempt> pending = new ArrayList<PendingAttempt>();
        int next = 0;
        pending.add(start(ordered.get(next), next++, capability, codes));

        ProviderAttempt early = await(pending.get(0).future,
                Math.min(properties.getHedgeDelayMs(), remainingMillis(deadline)));
        if (early != null) {
            completed.add(early);
            pending.clear();
        }
        if (!coversAll(completed, codes) && next < ordered.size()) {
            pending.add(start(ordered.get(next), next++, capability, codes));
        }

        while (!coversAll(completed, codes) && remainingMillis(deadline) > 0L) {
            drainCompleted(pending, completed);
            if (coversAll(completed, codes)) break;
            if (pending.isEmpty()) {
                if (next >= ordered.size()) break;
                pending.add(start(ordered.get(next), next++, capability, codes));
            }
            ProviderAttempt done = awaitAny(pending, remainingMillis(deadline));
            if (done == null) break;
            drainCompleted(pending, completed);
            if (!coversAll(completed, codes) && next < ordered.size() && pending.isEmpty()) {
                pending.add(start(ordered.get(next), next++, capability, codes));
            }
        }
        drainCompleted(pending, completed);
        return completed;
    }

    private PendingAttempt start(QuoteAdapter adapter, int order,
                                 MarketDataCapability capability, List<String> codes) {
        CompletableFuture<ProviderAttempt> future = CompletableFuture.supplyAsync(
                () -> fetchProvider(adapter, order, capability, codes), executor);
        return new PendingAttempt(future);
    }

    private ProviderAttempt fetchProvider(QuoteAdapter adapter, int order,
                                          MarketDataCapability capability, List<String> codes) {
        List<Quote> quotes = new ArrayList<Quote>();
        List<String> warnings = new ArrayList<String>();
        LocalDateTime retrievedAt = null;
        try {
            int batchSize = Math.max(1, adapter.batchLimit());
            for (int start = 0; start < codes.size(); start += batchSize) {
                List<String> batch = new ArrayList<String>(
                        codes.subList(start, Math.min(codes.size(), start + batchSize)));
                ProviderResult<List<Quote>> result = guard.execute(adapter, capability,
                        () -> adapter.fetchResult(batch));
                if (result.getData() != null) quotes.addAll(result.getData());
                warnings.addAll(result.getWarnings());
                if (retrievedAt == null || result.getRetrievedAt().isAfter(retrievedAt)) {
                    retrievedAt = result.getRetrievedAt();
                }
            }
            return ProviderAttempt.success(adapter, order, quotes,
                    retrievedAt == null ? LocalDateTime.now(clock) : retrievedAt, warnings);
        } catch (RuntimeException error) {
            return ProviderAttempt.failure(adapter, order, error);
        } catch (Exception error) {
            return ProviderAttempt.failure(adapter, order, error);
        }
    }

    private QuoteGatewayResult composeResult(MarketDataCapability capability, String type,
                                             List<String> codes, List<QuoteAdapter> ordered,
                                             List<ProviderAttempt> attempts, String refreshId,
                                             LocalDateTime startedAt) {
        Map<String, AcceptedQuote> fresh = acceptedQuotes(attempts, codes);
        List<Quote> output = new ArrayList<Quote>();
        Set<String> sources = new LinkedHashSet<String>();
        List<String> internalWarnings = new ArrayList<String>();
        int freshCount = 0;
        int staleCount = 0;
        int failedCount = 0;
        boolean usedFallback = false;
        long maxStaleAge = 0L;
        String primaryCode = ordered.isEmpty() ? null : ordered.get(0).providerCode();

        for (String code : codes) {
            AcceptedQuote accepted = fresh.get(code);
            if (accepted != null) {
                Quote quote = accepted.quote;
                quote.setSourceCode(accepted.provider.providerCode());
                quote.setRetrievedAt(accepted.retrievedAt);
                quote.setRefreshId(refreshId);
                boolean fallback = primaryCode != null && !primaryCode.equals(accepted.provider.providerCode());
                quote.setQualityStatus(fallback
                        ? MarketDataQualityStatus.FRESH_FALLBACK : MarketDataQualityStatus.FRESH_PRIMARY);
                if (fallback) {
                    quote.setWarning("主数据源响应失败或不完整，已自动切换备用数据源。");
                    usedFallback = true;
                }
                output.add(quote);
                sources.add(accepted.provider.providerCode());
                freshCount++;
                try {
                    snapshots.upsert(codec.quoteSnapshot(capability, scopeKey(type, code),
                            accepted.provider.providerCode(), accepted.provider.providerFamily(), quote,
                            accepted.retrievedAt, LocalDateTime.now(clock)));
                } catch (RuntimeException error) {
                    internalWarnings.add("本地兜底快照保存失败");
                    log.warn("Failed to persist market data snapshot for {}", code, error);
                }
                continue;
            }

            Optional<MarketDataSnapshot> stored = findSnapshot(capability, scopeKey(type, code));
            Optional<Quote> stale = stored.flatMap(codec::decodeQuote)
                    .flatMap(quote -> validator.accept(code, quote));
            if (stale.isPresent()) {
                MarketDataSnapshot snapshot = stored.get();
                Quote quote = stale.get();
                long age = Math.max(0L, Duration.between(snapshot.getRetrievedAt(),
                        LocalDateTime.now(clock)).getSeconds());
                quote.setSourceCode(snapshot.getProviderCode());
                quote.setRetrievedAt(snapshot.getRetrievedAt());
                quote.setQualityStatus(MarketDataQualityStatus.STALE_FALLBACK);
                quote.setStaleAgeSeconds(age);
                quote.setRefreshId(refreshId);
                quote.setWarning("实时数据源暂不可用，正在显示最近一次成功数据（已过期 " + age + " 秒）。");
                output.add(quote);
                sources.add(snapshot.getProviderCode());
                staleCount++;
                maxStaleAge = Math.max(maxStaleAge, age);
            } else {
                Quote unavailable = new Quote();
                unavailable.setInstrumentCode(code);
                unavailable.setValid(false);
                unavailable.setQualityStatus(MarketDataQualityStatus.UNAVAILABLE);
                unavailable.setRefreshId(refreshId);
                unavailable.setWarning("行情刷新失败，且没有可用的历史快照。");
                unavailable.setNote(unavailable.getWarning());
                output.add(unavailable);
                failedCount++;
            }
        }

        MarketDataQualityStatus status = aggregate(codes.size(), freshCount, staleCount, usedFallback);
        String warning = aggregateWarning(status, internalWarnings);
        LocalDateTime asOf = latestAsOf(output);
        LocalDateTime retrievedAt = latestRetrievedAt(output, startedAt);
        return new QuoteGatewayResult(output, status, String.join(",", sources), asOf, retrievedAt,
                staleCount > 0 ? maxStaleAge : null, warning, refreshId);
    }

    private Map<String, AcceptedQuote> acceptedQuotes(List<ProviderAttempt> attempts, List<String> codes) {
        List<ProviderAttempt> orderedAttempts = new ArrayList<ProviderAttempt>(attempts);
        orderedAttempts.sort(Comparator.comparingInt(value -> value.order));
        Set<String> requested = new LinkedHashSet<String>(codes);
        Map<String, AcceptedQuote> accepted = new LinkedHashMap<String, AcceptedQuote>();
        for (ProviderAttempt attempt : orderedAttempts) {
            if (attempt.error != null) continue;
            for (Quote quote : attempt.quotes) {
                String code = quote == null || quote.getInstrumentCode() == null
                        ? "" : quote.getInstrumentCode().trim().toUpperCase(Locale.ROOT);
                if (!requested.contains(code) || accepted.containsKey(code)) continue;
                validator.accept(code, quote).ifPresent(value -> accepted.put(code,
                        new AcceptedQuote(value, attempt.provider, attempt.retrievedAt)));
            }
        }
        return accepted;
    }

    private boolean coversAll(List<ProviderAttempt> attempts, List<String> codes) {
        return acceptedQuotes(attempts, codes).size() == codes.size();
    }

    private Optional<MarketDataSnapshot> findSnapshot(MarketDataCapability capability, String scopeKey) {
        try {
            return snapshots.find(capability, scopeKey);
        } catch (RuntimeException error) {
            log.warn("Failed to read market data snapshot for {}", scopeKey, error);
            return Optional.empty();
        }
    }

    private MarketDataQualityStatus aggregate(int requested, int fresh, int stale, boolean usedFallback) {
        if (fresh == requested && requested > 0) {
            return usedFallback ? MarketDataQualityStatus.FRESH_FALLBACK
                    : MarketDataQualityStatus.FRESH_PRIMARY;
        }
        if (fresh > 0) return MarketDataQualityStatus.PARTIAL_FRESH;
        if (stale > 0) return MarketDataQualityStatus.STALE_FALLBACK;
        return MarketDataQualityStatus.UNAVAILABLE;
    }

    private String aggregateWarning(MarketDataQualityStatus status, List<String> internalWarnings) {
        String warning;
        switch (status) {
            case FRESH_FALLBACK:
                warning = "主数据源响应失败或不完整，系统已自动切换备用数据源。";
                break;
            case PARTIAL_FRESH:
                warning = "本次刷新仅部分成功，缺失标的已尽可能使用最近成功数据补齐。";
                break;
            case STALE_FALLBACK:
                warning = "实时数据源暂不可用，当前显示最近一次成功数据。";
                break;
            case UNAVAILABLE:
                warning = "行情刷新失败，当前没有可用数据。";
                break;
            default:
                warning = null;
        }
        if (!internalWarnings.isEmpty()) {
            String detail = String.join("；", new LinkedHashSet<String>(internalWarnings));
            warning = warning == null ? detail : warning + " " + detail + "。";
        }
        return warning;
    }

    private Long createAudit(MarketDataCapability capability, String scope, LocalDateTime startedAt) {
        try {
            return refreshRuns.create(capability, scope, "MANUAL", startedAt);
        } catch (RuntimeException error) {
            log.warn("Failed to create market data refresh audit", error);
            return null;
        }
    }

    private void finishAudit(Long runId, QuoteGatewayResult result,
                             List<ProviderAttempt> attempts, int requestedCount) {
        if (runId == null) return;
        int fresh = 0;
        int stale = 0;
        int failed = 0;
        for (Quote quote : result.getQuotes()) {
            if (quote.getQualityStatus() == MarketDataQualityStatus.STALE_FALLBACK) stale++;
            else if (quote.getQualityStatus() == MarketDataQualityStatus.UNAVAILABLE) failed++;
            else fresh++;
        }
        Set<String> selected = new LinkedHashSet<String>();
        for (ProviderAttempt attempt : attempts) selected.add(attempt.provider.providerCode());
        try {
            refreshRuns.finish(runId, result.getQualityStatus().name(), requestedCount, fresh, stale, failed,
                    String.join(",", selected), result.getWarning(), LocalDateTime.now(clock));
        } catch (RuntimeException error) {
            log.warn("Failed to finish market data refresh audit {}", runId, error);
        }
    }

    private ProviderAttempt await(CompletableFuture<ProviderAttempt> future, long timeoutMillis) {
        if (timeoutMillis <= 0L) return null;
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException error) {
            return null;
        } catch (Exception error) {
            return null;
        }
    }

    private ProviderAttempt awaitAny(List<PendingAttempt> pending, long timeoutMillis) {
        if (pending.isEmpty() || timeoutMillis <= 0L) return null;
        CompletableFuture<?>[] values = pending.stream()
                .map(value -> value.future).toArray(CompletableFuture[]::new);
        try {
            return (ProviderAttempt) CompletableFuture.anyOf(values)
                    .get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Exception error) {
            return null;
        }
    }

    private void drainCompleted(List<PendingAttempt> pending, List<ProviderAttempt> completed) {
        Iterator<PendingAttempt> iterator = pending.iterator();
        while (iterator.hasNext()) {
            PendingAttempt value = iterator.next();
            if (value.future.isDone()) {
                ProviderAttempt attempt = value.future.getNow(null);
                if (attempt != null) completed.add(attempt);
                iterator.remove();
            }
        }
    }

    private long remainingMillis(long deadlineNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
    }

    private String normalizeType(String type) {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("instrument type is required");
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> normalizeCodes(List<String> codes) {
        if (codes == null) return Collections.emptyList();
        Set<String> normalized = new LinkedHashSet<String>();
        for (String code : codes) {
            if (code != null && !code.trim().isEmpty()) {
                normalized.add(code.trim().toUpperCase(Locale.ROOT));
            }
        }
        return new ArrayList<String>(normalized);
    }

    private MarketDataCapability quoteCapability(String type) {
        switch (type) {
            case "STOCK": return MarketDataCapability.REALTIME_STOCK_QUOTE;
            case "INDEX": return MarketDataCapability.REALTIME_INDEX_QUOTE;
            case "FUND": return MarketDataCapability.REALTIME_FUND_ESTIMATE;
            case "SECTOR": return MarketDataCapability.REALTIME_SECTOR_QUOTE;
            default: throw new IllegalArgumentException("unsupported instrument type: " + type);
        }
    }

    private String scopeKey(String type, String code) { return type + ":" + code; }

    private LocalDateTime latestAsOf(List<Quote> quotes) {
        LocalDateTime latest = null;
        for (Quote quote : quotes) {
            if (quote.getAsOf() != null && (latest == null || quote.getAsOf().isAfter(latest))) {
                latest = quote.getAsOf();
            }
        }
        return latest;
    }

    private LocalDateTime latestRetrievedAt(List<Quote> quotes, LocalDateTime fallback) {
        LocalDateTime latest = null;
        for (Quote quote : quotes) {
            if (quote.getRetrievedAt() != null
                    && (latest == null || quote.getRetrievedAt().isAfter(latest))) {
                latest = quote.getRetrievedAt();
            }
        }
        return latest == null ? fallback : latest;
    }

    private static final class PendingAttempt {
        private final CompletableFuture<ProviderAttempt> future;
        private PendingAttempt(CompletableFuture<ProviderAttempt> future) { this.future = future; }
    }

    private static final class ProviderAttempt {
        private final QuoteAdapter provider;
        private final int order;
        private final List<Quote> quotes;
        private final LocalDateTime retrievedAt;
        private final List<String> warnings;
        private final Throwable error;

        private ProviderAttempt(QuoteAdapter provider, int order, List<Quote> quotes,
                                LocalDateTime retrievedAt, List<String> warnings, Throwable error) {
            this.provider = provider;
            this.order = order;
            this.quotes = quotes;
            this.retrievedAt = retrievedAt;
            this.warnings = warnings;
            this.error = error;
        }

        static ProviderAttempt success(QuoteAdapter provider, int order, List<Quote> quotes,
                                       LocalDateTime retrievedAt, List<String> warnings) {
            return new ProviderAttempt(provider, order, quotes, retrievedAt, warnings, null);
        }

        static ProviderAttempt failure(QuoteAdapter provider, int order, Throwable error) {
            return new ProviderAttempt(provider, order, Collections.<Quote>emptyList(),
                    null, Collections.<String>emptyList(), error);
        }
    }

    private static final class AcceptedQuote {
        private final Quote quote;
        private final QuoteAdapter provider;
        private final LocalDateTime retrievedAt;

        private AcceptedQuote(Quote quote, QuoteAdapter provider, LocalDateTime retrievedAt) {
            this.quote = quote;
            this.provider = provider;
            this.retrievedAt = retrievedAt;
        }
    }

    private static final class CacheEntry {
        private final QuoteGatewayResult result;
        private final long createdAtMillis;
        private CacheEntry(QuoteGatewayResult result, long createdAtMillis) {
            this.result = result;
            this.createdAtMillis = createdAtMillis;
        }
    }

    private static final class SectorCacheEntry {
        private final SectorCatalogGatewayResult result;
        private final long createdAtMillis;
        private SectorCacheEntry(SectorCatalogGatewayResult result, long createdAtMillis) {
            this.result = result;
            this.createdAtMillis = createdAtMillis;
        }
    }
}
