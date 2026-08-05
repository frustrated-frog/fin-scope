package com.finscope.service.radar;

import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.dao.radar.RadarRefreshRunRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.domain.radar.RadarRefreshStep;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.dedupe.FingerprintService;
import com.finscope.service.news.NewsFeedItem;
import com.finscope.service.news.NewsFeedService;
import com.finscope.service.news.NewsFeedSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RadarHotspotProductionPipelineTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 0);

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
        RadarHotspotPersistenceService persistence = new RadarHotspotPersistenceService(repository);
        RadarHotspotProductionPipeline pipeline = new RadarHotspotProductionPipeline(news, repository, clustering,
                priority, watchlist, runs, enhancement, scores, persistence);

        NewsFeedItem first = item("CLS:1", "CLS", "财联社", "宁德时代发布新一代电池", now.minusMinutes(20));
        NewsFeedItem second = item("THS:2", "THS", "同花顺", "宁德时代新电池正式发布", now.minusMinutes(25));
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
        assertTrue(result.getEvents().get(0).getHotspotScore() >= 75);
        verify(repository).replaceEventSignals(eq(11L), any());
        verify(repository).expireEventsExcept(any(), eq(now.minusHours(48)), eq(now));
        org.mockito.InOrder order = inOrder(runs);
        order.verify(runs).startStep(7L, "FETCH", now);
        order.verify(runs).startStep(7L, "NORMALIZE", now);
        order.verify(runs).startStep(7L, "AGGREGATE", now);
        order.verify(runs).startStep(7L, "RANK", now);
        order.verify(runs).startStep(7L, "PERSIST", now);
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
}
