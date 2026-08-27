package com.finscope.service.radar;

import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.dao.radar.RadarEventSnapshotRepository;
import com.finscope.dao.radar.RadarRefreshRunRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.domain.radar.RadarRefreshStep;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.dedupe.FingerprintService;
import com.finscope.service.news.NewsFeedItem;
import com.finscope.service.news.NewsFeedService;
import com.finscope.service.news.NewsFeedSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RadarHotspotProductionPipelineTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 0);

    @Test
    void mergesClustersThatResolveToTheSameEventIdentity() {
        RadarSignal firstSignal = signal(1L, item("CLS:1", "CLS", "财联社",
                "中国交建上半年新签合同额下降", now.minusMinutes(20)), 1);
        RadarSignal secondSignal = signal(2L, item("THS:2", "THS", "同花顺",
                "中国交建上半年新签合同额同比下降", now.minusMinutes(10)), 1);
        RadarClusteringService.ClusterResult first = cluster(firstSignal,
                "601800:公告:信息:20260814:20.61%");
        RadarClusteringService.ClusterResult second = cluster(secondSignal,
                "601800:公告:信息:20260814:20.61%");
        RadarHotspotProductionPipeline pipeline = new RadarHotspotProductionPipeline();

        List<RadarClusteringService.ClusterResult> merged = pipeline.mergeDuplicateClusters(
                Arrays.asList(first, second));

        assertEquals(1, merged.size());
        assertEquals(2, merged.get(0).getSignals().size());
        assertEquals(2, merged.get(0).getLinks().size());
        assertEquals(2, merged.get(0).getEvent().getSignalCount());
        assertEquals(2, merged.get(0).getEvent().getSourceCount());
    }

    @Test
    void runsFetchNormalizeAggregateRankAndPersistAsOneProductionBatch() {
        NewsFeedService news = mock(NewsFeedService.class);
        RadarRepository repository = mock(RadarRepository.class);
        RadarClusteringService clustering = new RadarClusteringService(new RadarTextAnalyzer(new FingerprintService()));
        RadarPriorityService priority = new RadarPriorityService();
        WatchlistRepository watchlist = mock(WatchlistRepository.class);
        RadarRefreshRunRepository runs = mock(RadarRefreshRunRepository.class);
        RadarEventEnhancementScheduler enhancement = mock(RadarEventEnhancementScheduler.class);
        RadarHotspotScoreService scores = new RadarHotspotScoreService();
        RadarDashboardCategoryService dashboardCategories = new RadarDashboardCategoryService();
        RadarHotspotPersistenceService persistence = new RadarHotspotPersistenceService(repository);
        RadarEventSnapshotRepository snapshots = mock(RadarEventSnapshotRepository.class);
        RadarHotspotProductionPipeline pipeline = new RadarHotspotProductionPipeline();
        wire(pipeline, news, repository, clustering, priority, watchlist, runs, enhancement,
                scores, dashboardCategories, persistence, snapshots);

        NewsFeedItem first = item("CLS:1", "CLS_NEWS_FLASH", "财联社",
                "宁德时代发布新一代电池", now.minusMinutes(20));
        NewsFeedItem second = item("EASTMONEY:2", "EASTMONEY_NEWS_FLASH", "东方财富",
                "宁德时代新电池正式发布", now.minusMinutes(25));
        when(news.load("ALL", 100)).thenReturn(new NewsFeedSnapshot(Arrays.asList(first, second),
                Collections.<String>emptyList(), now, 2));
        when(watchlist.findByTypes(Arrays.asList("STOCK", "FUND"))).thenReturn(Collections.emptyList());

        RadarRefreshRun run = new RadarRefreshRun();
        run.setId(7L); run.setRunKey("run-1"); run.setStatus("RUNNING");
        when(runs.startRun(anyString(), eq("TEST"), eq(now))).thenReturn(run);
        when(runs.startStep(anyLong(), anyString(), eq(now))).thenAnswer(invocation -> new RadarRefreshStep());
        when(runs.completeStep(anyLong(), anyString(), anyString(), anyInt(), anyInt(), anyString(), eq(now)))
                .thenAnswer(invocation -> new RadarRefreshStep());

        AtomicLong ids = new AtomicLong();
        when(repository.findSignalByItemId(anyString())).thenReturn(Optional.empty());
        when(repository.capture(any(RadarSignal.class), eq(now))).thenAnswer(invocation -> {
            RadarSignal value = invocation.getArgument(0);
            value.setId(ids.incrementAndGet());
            return value;
        });
        when(repository.findActiveSignals(now.minusHours(48), 500)).thenAnswer(invocation -> Arrays.asList(
                signal(1L, first, 1), signal(2L, second, 1)));

        RadarEvent legacy = new RadarEvent();
        legacy.setId(10L); legacy.setEventKey("宁德时代:发布:电池");
        legacy.setLastSeenAt(now.minusMinutes(20));
        when(repository.findEventByKey("宁德时代:发布:电池")).thenReturn(Optional.of(legacy));

        RadarEvent event = new RadarEvent();
        event.setEventKey("COMPANY:宁德时代:发布:电池"); event.setCanonicalTitle(first.getTitle());
        event.setSummary(first.getContent()); event.setCategoryCode("COMPANY"); event.setStatus("ACTIVE");
        event.setFirstSeenAt(first.getPublishedAt()); event.setLastSeenAt(second.getPublishedAt());
        when(repository.saveEvent(any(RadarEvent.class))).thenAnswer(invocation -> {
            RadarEvent value = invocation.getArgument(0); value.setId(11L); return value;
        });
        when(runs.completeRun(7L, 2, 2, 1, "", now)).thenReturn(run);

        RadarHotspotProductionPipeline.ProductionResult result = pipeline.run("ALL", "TEST", now);

        assertEquals(1, result.getEvents().size());
        assertEquals(2, result.getEvents().get(0).getSourceCount());
        assertEquals(2, result.getEvents().get(0).getSignalCount());
        assertTrue(result.getEvents().get(0).getHotspotScore() >= 75);
        assertTrue(result.getEvents().get(0).getConfidenceScore() > 0);
        assertEquals("HOTSPOT_V2", result.getEvents().get(0).getScoreVersion());
        assertEquals("宁德时代:发布:电池", result.getEvents().get(0).getEventKey());
        assertEquals("TECHNOLOGY", result.getEvents().get(0).getDashboardCategory());
        ArgumentCaptor<RadarSignal> capturedSignals = ArgumentCaptor.forClass(RadarSignal.class);
        verify(repository, times(2)).capture(capturedSignals.capture(), eq(now));
        Set<String> providers = new HashSet<String>();
        List<RadarSignal> capturedValues = capturedSignals.getAllValues();
        for (RadarSignal captured : capturedValues) {
            providers.add(captured.getProviderCode());
        }
        assertEquals(new HashSet<String>(Arrays.asList("CLS_NEWS_FLASH", "EASTMONEY_NEWS_FLASH")), providers);
        verify(repository).replaceEventSignals(eq(11L), any());
        ArgumentCaptor<com.finscope.domain.radar.RadarEventSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(com.finscope.domain.radar.RadarEventSnapshot.class);
        verify(snapshots).save(snapshotCaptor.capture());
        assertEquals("HOTSPOT_V2", snapshotCaptor.getValue().getScoreVersion());
        verify(repository).expireEventsExcept(any(), eq(now.minusHours(48)), eq(now));
        org.mockito.InOrder order = inOrder(runs);
        order.verify(runs).startStep(7L, "FETCH", now);
        order.verify(runs).startStep(7L, "NORMALIZE", now);
        order.verify(runs).startStep(7L, "AGGREGATE", now);
        order.verify(runs).startStep(7L, "RANK", now);
        order.verify(runs).startStep(7L, "PERSIST", now);
    }

    @Test
    void preservesNativeIdentityWhenAnotherClusterTriesToReuseItAsLegacyIdentity() {
        NewsFeedService news = mock(NewsFeedService.class);
        RadarRepository repository = mock(RadarRepository.class);
        RadarClusteringService clustering = mock(RadarClusteringService.class);
        RadarPriorityService priority = new RadarPriorityService();
        WatchlistRepository watchlist = mock(WatchlistRepository.class);
        RadarRefreshRunRepository runs = mock(RadarRefreshRunRepository.class);
        RadarHotspotProductionPipeline pipeline = new RadarHotspotProductionPipeline();
        wire(pipeline, news, repository, clustering, priority, watchlist, runs,
                mock(RadarEventEnhancementScheduler.class), new RadarHotspotScoreService(),
                new RadarDashboardCategoryService(), new RadarHotspotPersistenceService(repository),
                mock(RadarEventSnapshotRepository.class));
        NewsFeedSnapshot feed = new NewsFeedSnapshot(Collections.emptyList(), Collections.emptyList(), now, 0);
        when(news.load("ALL", 100)).thenReturn(feed);
        when(watchlist.findByTypes(Arrays.asList("STOCK", "FUND"))).thenReturn(Collections.emptyList());
        RadarRefreshRun run = new RadarRefreshRun(); run.setId(8L); run.setStatus("RUNNING");
        when(runs.startRun(anyString(), eq("TEST"), eq(now))).thenReturn(run);
        when(runs.startStep(anyLong(), anyString(), eq(now))).thenReturn(new RadarRefreshStep());
        when(runs.completeStep(anyLong(), anyString(), anyString(), anyInt(), anyInt(), anyString(), eq(now)))
                .thenReturn(new RadarRefreshStep());
        RadarSignal first = signal(1L, item("A:1", "A", "A", "英伟达发布业绩信息", now.minusMinutes(10)), 1);
        RadarSignal second = signal(2L, item("B:2", "B", "B", "英伟达发布业绩信息增长5%", now.minusMinutes(8)), 1);
        when(repository.findActiveSignals(now.minusHours(48), 500)).thenReturn(Arrays.asList(first, second));
        RadarClusteringService.ClusterResult nativeCluster = cluster(first,
                "英伟达:事件:信息:20260805");
        RadarClusteringService.ClusterResult detailedCluster = cluster(second,
                "英伟达:事件:信息:20260805:5%");
        when(clustering.cluster(any())).thenReturn(Arrays.asList(nativeCluster, detailedCluster));
        RadarEvent legacy = new RadarEvent(); legacy.setId(10L); legacy.setEventKey("英伟达:事件:信息:20260805");
        legacy.setLastSeenAt(now.minusMinutes(20));
        when(repository.findEventByKey("英伟达:事件:信息:20260805")).thenReturn(Optional.of(legacy));
        when(repository.saveEvent(any(RadarEvent.class))).thenAnswer(invocation -> {
            RadarEvent value = invocation.getArgument(0); value.setId(value.getEventKey().equals(legacy.getEventKey()) ? 10L : 11L); return value;
        });
        when(runs.completeRun(eq(8L), anyInt(), eq(2), eq(2), anyString(), eq(now))).thenReturn(run);

        RadarHotspotProductionPipeline.ProductionResult result = assertDoesNotThrow(
                () -> pipeline.run("ALL", "TEST", now));

        Set<String> keys = new HashSet<String>();
        for (RadarEvent event : result.getEvents()) keys.add(event.getEventKey());
        assertEquals(2, keys.size());
        assertTrue(keys.contains("英伟达:事件:信息:20260805"));
        assertTrue(keys.contains("英伟达:事件:信息:20260805:5%"));
    }

    private NewsFeedItem item(String id, String provider, String source, String title, LocalDateTime time) {
        return new NewsFeedItem(id, "FLASH", title, title, "https://example.com/" + id, time,
                provider, source, "TIER_1", "COMPANY", "公司", 0.95, "测试");
    }

    private RadarSignal signal(Long id, NewsFeedItem item, int rank) {
        RadarSignal signal = new RadarSignal(); signal.setId(id); signal.setItemId(item.getId());
        signal.setProviderCode(item.getProviderCode()); signal.setSourceName(item.getSourceName());
        signal.setSourceTier(item.getSourceTier()); signal.setCategoryCode(item.getCategoryCode());
        signal.setTitle(item.getTitle()); signal.setContent(item.getContent()); signal.setPublishedAt(item.getPublishedAt());
        signal.setFirstSeenAt(item.getPublishedAt()); signal.setLastSeenAt(item.getPublishedAt()); signal.setSourceRank(rank);
        signal.setSourceWeight(1.0D); signal.setStatus("ACTIVE"); return signal;
    }

    private RadarClusteringService.ClusterResult cluster(RadarSignal signal, String eventKey) {
        RadarClusteringService.ClusterResult cluster = new RadarClusteringService.ClusterResult(signal);
        cluster.getEvent().setEventKey(eventKey);
        cluster.getEvent().setSignalCount(1);
        cluster.getEvent().setSourceCount(1);
        RadarEventSignal link = new RadarEventSignal();
        link.setSignalId(signal.getId());
        link.setRelationType("PRIMARY");
        link.setMatchScore(1.0D);
        link.setMatchReason("代表信号");
        cluster.getLinks().add(link);
        return cluster;
    }

    private void wire(RadarHotspotProductionPipeline pipeline,
                      NewsFeedService news,
                      RadarRepository repository,
                      RadarClusteringService clustering,
                      RadarPriorityService priority,
                      WatchlistRepository watchlist,
                      RadarRefreshRunRepository runs,
                      RadarEventEnhancementScheduler enhancement,
                      RadarHotspotScoreService scores,
                      RadarDashboardCategoryService dashboardCategories,
                      RadarHotspotPersistenceService persistence,
                      RadarEventSnapshotRepository snapshots) {
        ReflectionTestUtils.setField(pipeline, "news", news);
        ReflectionTestUtils.setField(pipeline, "repository", repository);
        ReflectionTestUtils.setField(pipeline, "clustering", clustering);
        RadarEventBatchIdentityResolver identityResolver = new RadarEventBatchIdentityResolver();
        ReflectionTestUtils.setField(identityResolver, "repository", repository);
        ReflectionTestUtils.setField(pipeline, "identityResolver", identityResolver);
        ReflectionTestUtils.setField(pipeline, "priority", priority);
        ReflectionTestUtils.setField(pipeline, "watchlist", watchlist);
        ReflectionTestUtils.setField(pipeline, "runs", runs);
        ReflectionTestUtils.setField(pipeline, "enhancement", enhancement);
        ReflectionTestUtils.setField(pipeline, "hotspotScores", scores);
        ReflectionTestUtils.setField(pipeline, "dashboardCategories", dashboardCategories);
        ReflectionTestUtils.setField(pipeline, "persistence", persistence);
        ReflectionTestUtils.setField(pipeline, "snapshots", snapshots);
    }
}
