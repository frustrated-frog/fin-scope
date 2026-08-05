package com.finscope.service.radar;

import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.dao.radar.RadarEventSnapshotRepository;
import com.finscope.dao.radar.RadarRefreshRunRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarEventSnapshot;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.news.NewsFeedItem;
import com.finscope.service.news.NewsFeedService;
import com.finscope.service.news.NewsFeedSnapshot;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class RadarHotspotProductionPipeline {
    private final NewsFeedService news;
    private final RadarRepository repository;
    private final RadarClusteringService clustering;
    private final RadarPriorityService priority;
    private final WatchlistRepository watchlist;
    private static final int SIGNAL_WINDOW_HOURS = 48;
    private static final int SNAPSHOT_KEEP_DAYS = 7;
    private final RadarRefreshRunRepository runs;
    private final RadarEventEnhancementScheduler enhancement;
    private final RadarHotspotScoreService hotspotScores;
    private final RadarDashboardCategoryService dashboardCategories;
    private final RadarHotspotPersistenceService persistence;
    private final RadarEventSnapshotRepository snapshots;

    public RadarHotspotProductionPipeline(NewsFeedService news, RadarRepository repository,
                                          RadarClusteringService clustering, RadarPriorityService priority,
                                          WatchlistRepository watchlist, RadarRefreshRunRepository runs,
                                          RadarEventEnhancementScheduler enhancement,
                                          RadarHotspotScoreService hotspotScores,
                                          RadarDashboardCategoryService dashboardCategories,
                                          RadarHotspotPersistenceService persistence,
                                          RadarEventSnapshotRepository snapshots) {
        this.news = news; this.repository = repository; this.clustering = clustering;
        this.priority = priority; this.watchlist = watchlist; this.runs = runs;
        this.enhancement = enhancement; this.hotspotScores = hotspotScores;
        this.dashboardCategories = dashboardCategories;
        this.persistence = persistence; this.snapshots = snapshots;
    }

    public ProductionResult run(String requestedCategory, String triggerType, LocalDateTime now) {
        String category = normalizeCategory(requestedCategory);
        RadarRefreshRun run = runs.startRun("radar-" + UUID.randomUUID().toString(), triggerType, now);
        try {
            runs.startStep(run.getId(), "FETCH", now);
            NewsFeedSnapshot snapshot = news.load(category, 100);
            runs.completeStep(run.getId(), "FETCH", "SUCCESS", 0, snapshot.getItems().size(),
                    "warnings=" + snapshot.getWarnings().size(), now);

            runs.startStep(run.getId(), "NORMALIZE", now);
            List<RadarSignal> captured = captureSignals(snapshot.getItems(), now);
            repository.expireSignals(now.minusHours(SIGNAL_WINDOW_HOURS), now);
            List<RadarSignal> active = repository.findActiveSignals(now.minusHours(SIGNAL_WINDOW_HOURS), 500);
            runs.completeStep(run.getId(), "NORMALIZE", "SUCCESS", snapshot.getItems().size(), active.size(),
                    "dedupe=provider+item", now);

            runs.startStep(run.getId(), "AGGREGATE", now);
            List<RadarClusteringService.ClusterResult> clusters = clustering.cluster(active);
            runs.completeStep(run.getId(), "AGGREGATE", "SUCCESS", active.size(), clusters.size(),
                    "connected-components=true", now);

            runs.startStep(run.getId(), "RANK", now);
            List<WatchlistItem> followed = watchlist.findByTypes(Arrays.asList("STOCK", "FUND"));
            List<RankedCluster> ranked = rank(clusters, followed, now);
            runs.completeStep(run.getId(), "RANK", "SUCCESS", clusters.size(), ranked.size(),
                    "hotspot-score=deterministic", now);

            runs.startStep(run.getId(), "PERSIST", now);
            List<RadarEvent> savedEvents = persist(ranked, now);
            Set<String> activeEventKeys = new HashSet<String>();
            for (RadarEvent event : savedEvents) activeEventKeys.add(event.getEventKey());
            repository.expireEventsExcept(activeEventKeys, now.minusHours(SIGNAL_WINDOW_HOURS), now);
            if (snapshots != null) snapshots.deleteExpired(now.minusDays(SNAPSHOT_KEEP_DAYS));
            runs.completeStep(run.getId(), "PERSIST", "SUCCESS", ranked.size(), savedEvents.size(),
                    "snapshot=latest-completed", now);
            String warning = joinWarnings(snapshot.getWarnings());
            RadarRefreshRun completed = runs.completeRun(run.getId(), providerCount(captured), active.size(),
                    savedEvents.size(), warning, now);
            return new ProductionResult(completed, snapshot, savedEvents);
        } catch (RuntimeException error) {
            runs.failRun(run.getId(), safeMessage(error), now);
            throw error;
        }
    }

    private List<RadarSignal> captureSignals(List<NewsFeedItem> items, LocalDateTime now) {
        List<NewsFeedItem> ordered = new ArrayList<NewsFeedItem>(items == null
                ? Collections.<NewsFeedItem>emptyList() : items);
        ordered.sort(Comparator.comparing(NewsFeedItem::getPublishedAt,
                Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(NewsFeedItem::getId,
                Comparator.nullsLast(Comparator.naturalOrder())));
        Map<String, Integer> ranks = new LinkedHashMap<String, Integer>();
        List<RadarSignal> captured = new ArrayList<RadarSignal>();
        for (NewsFeedItem item : ordered) {
            String provider = firstNonBlank(item.getProviderCode(), item.getSourceName()).toUpperCase(Locale.ROOT);
            int rank = nextRank(ranks, provider);
            RadarSignal signal = toSignal(item, rank);
            Optional<RadarSignal> previous = repository.findSignalByItemId(item.getId());
            if (previous.isPresent()) signal.setPreviousSourceRank(previous.get().getSourceRank());
            captured.add(repository.capture(signal, now));
        }
        return captured;
    }

    private List<RankedCluster> rank(List<RadarClusteringService.ClusterResult> clusters,
                                     List<WatchlistItem> followed, LocalDateTime now) {
        List<RankedCluster> values = new ArrayList<RankedCluster>();
        for (RadarClusteringService.ClusterResult cluster : clusters) {
            RadarEvent event = cluster.getEvent();
            event.setDashboardCategory(dashboardCategories.classify(event));
            RadarHotspotScoreService.Score hotspot = hotspotScores.score(cluster.getSignals(), now,
                    previousSnapshot(event, now));
            event.setHotspotScore(hotspot.getTotalScore()); event.setHotspotExplanation(hotspot.getExplanation());
            event.setHotspotLifecycleState(hotspot.getLifecycleState());
            RadarPriorityService.PriorityResult result = priority.score(event, cluster.getSignals(), followed, now);
            event.setPriorityScore(result.getTotalScore());
            event.setScoreExplanation(hotspot.getExplanation() + "；" + String.join("；", result.getReasons()));
            event.setWatchlistRelevance(result.getWatchlistScore()); event.setWatchlistExplanation(result.getWatchlistExplanation());
            event.setUncertainty(result.getUncertainty()); event.setNextObservation(result.getNextObservation()); event.setUpdatedAt(now);
            values.add(new RankedCluster(cluster, hotspot));
        }
        values.sort(Comparator.comparingInt((RankedCluster value) -> value.cluster.getEvent().getPriorityScore()).reversed()
                .thenComparing(Comparator.comparingInt((RankedCluster value) -> value.cluster.getEvent().getHotspotScore()).reversed())
                .thenComparing(value -> value.cluster.getEvent().getEventKey(), Comparator.nullsLast(Comparator.naturalOrder())));
        return values;
    }

    private List<RadarEvent> persist(List<RankedCluster> ranked, LocalDateTime now) {
        if (ranked.isEmpty()) return new ArrayList<RadarEvent>();
        List<RadarEvent> events = new ArrayList<RadarEvent>();
        Map<String, List<RadarEventSignal>> linksByEventKey = new LinkedHashMap<String, List<RadarEventSignal>>();
        for (RankedCluster rankedCluster : ranked) {
            RadarClusteringService.ClusterResult cluster = rankedCluster.cluster;
            events.add(cluster.getEvent());
            linksByEventKey.put(cluster.getEvent().getEventKey(), cluster.getLinks());
        }
        // 事件与信号关系在单个事务内写入，避免出现事件已写、关系未写的半状态。
        List<RadarEvent> saved = persistence.persistEvents(events, linksByEventKey);
        int evidenceSchedules = 0;
        for (int index = 0; index < saved.size(); index++) {
            RadarEvent value = saved.get(index);
            RadarClusteringService.ClusterResult cluster = ranked.get(index).cluster;
            RadarHotspotScoreService.Score hotspot = ranked.get(index).hotspot;
            if (snapshots != null) {
                snapshots.save(hotspot.toSnapshot(value.getId(), value.getSignalCount(), value.getSourceCount(), now));
            }
            boolean includeEvidence = evidenceSchedules < 2 && value.getPriorityScore() >= 75;
            if (enhancement != null && (cluster.getSignals().size() > 1 || includeEvidence)) {
                enhancement.schedule(value, cluster.getSignals(), now, includeEvidence);
                if (includeEvidence) evidenceSchedules++;
            }
        }
        return saved;
    }

    private RadarEventSnapshot previousSnapshot(RadarEvent event, LocalDateTime now) {
        if (event == null || event.getEventKey() == null || snapshots == null) return null;
        Optional<RadarEvent> stored = repository.findEventByKey(event.getEventKey());
        if (!stored.isPresent() || stored.get().getId() == null) return null;
        return snapshots.findLatestBefore(stored.get().getId(), now).orElse(null);
    }

    private RadarSignal toSignal(NewsFeedItem item, int rank) {
        RadarSignal signal = new RadarSignal(); signal.setItemId(item.getId()); signal.setProviderCode(item.getProviderCode());
        signal.setSourceName(item.getSourceName()); signal.setSourceTier(item.getSourceTier()); signal.setCategoryCode(item.getCategoryCode());
        signal.setTitle(item.getTitle()); signal.setContent(item.getContent()); signal.setUrl(item.getUrl()); signal.setPublishedAt(item.getPublishedAt());
        signal.setSourceRank(rank); signal.setSourceWeight(sourceWeight(item.getSourceTier())); signal.setContentHash(hash(item.getTitle()+"\n"+item.getContent()+"\n"+item.getUrl()));
        signal.setStatus("ACTIVE"); return signal;
    }

    private int nextRank(Map<String, Integer> ranks, String provider) {
        int next = ranks.containsKey(provider) ? ranks.get(provider) + 1 : 1;
        ranks.put(provider, next); return next;
    }
    private double sourceWeight(String tier) {
        return RadarSourceQuality.resolve(tier).getHotnessWeight();
    }
    private int providerCount(List<RadarSignal> signals) {
        Set<String> providers = new HashSet<String>();
        for (RadarSignal signal : signals) providers.add(firstNonBlank(signal.getProviderCode(), signal.getSourceName()));
        return providers.size();
    }
    private String joinWarnings(List<String> warnings) { return warnings == null ? "" : String.join("；", warnings); }
    private String normalizeCategory(String value) { return value == null || value.trim().isEmpty() ? "ALL" : value.trim().toUpperCase(Locale.ROOT); }
    private String firstNonBlank(String first, String second) { return first == null || first.trim().isEmpty() ? (second == null ? "" : second.trim()) : first.trim(); }
    private String safeMessage(RuntimeException error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
    private String hash(String value) {
        try { byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(); for (byte item : digest) result.append(String.format("%02x", item)); return result.toString();
        } catch (Exception error) { throw new IllegalStateException("无法生成雷达内容指纹", error); }
    }

    private static final class RankedCluster {
        private final RadarClusteringService.ClusterResult cluster;
        private final RadarHotspotScoreService.Score hotspot;
        private RankedCluster(RadarClusteringService.ClusterResult cluster, RadarHotspotScoreService.Score hotspot) {
            this.cluster = cluster; this.hotspot = hotspot;
        }
    }

    public static final class ProductionResult {
        private final RadarRefreshRun run;
        private final NewsFeedSnapshot snapshot;
        private final List<RadarEvent> events;
        private ProductionResult(RadarRefreshRun run, NewsFeedSnapshot snapshot, List<RadarEvent> events) {
            this.run = run; this.snapshot = snapshot; this.events = Collections.unmodifiableList(new ArrayList<RadarEvent>(events));
        }
        public RadarRefreshRun getRun() { return run; }
        public NewsFeedSnapshot getSnapshot() { return snapshot; }
        public List<RadarEvent> getEvents() { return events; }
    }
}
