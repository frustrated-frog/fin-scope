package com.finscope.service.radar;

import com.finscope.dao.radar.RadarRefreshRunRepository;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarInterpretationBatchMessage;
import com.finscope.domain.radar.RadarInterpretationBatchPublisher;
import com.finscope.service.news.NewsFeedSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

class RadarHotspotRefreshServiceTest {
    @Test
    void coalescesManualAndScheduledRequestsIntoOneBackgroundRun() {
        RadarHotspotProductionPipeline pipeline = mock(RadarHotspotProductionPipeline.class);
        RadarRefreshRunRepository runs = mock(RadarRefreshRunRepository.class);
        List<Runnable> queued = new ArrayList<Runnable>();
        Executor executor = queued::add;
        Clock clock = Clock.fixed(Instant.parse("2026-08-05T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        RadarHotspotRefreshService service = new RadarHotspotRefreshService(pipeline, runs, executor, clock);

        assertTrue(service.requestRefresh());
        assertFalse(service.requestRefresh());
        assertTrue(service.isRunning());
        assertEquals(1, queued.size());

        queued.get(0).run();

        verify(pipeline).run("ALL", "MANUAL", service.now());
        assertFalse(service.isRunning());
    }

    @Test
    void exposesTheLatestCompletedBatchForThePageStatus() {
        RadarHotspotProductionPipeline pipeline = mock(RadarHotspotProductionPipeline.class);
        RadarRefreshRunRepository runs = mock(RadarRefreshRunRepository.class);
        RadarRefreshRun latest = new RadarRefreshRun(); latest.setStatus("SUCCESS");
        when(runs.findLatestCompletedRun()).thenReturn(java.util.Optional.of(latest));

        RadarHotspotRefreshService service = new RadarHotspotRefreshService(pipeline, runs, Runnable::run,
                Clock.systemDefaultZone());

        assertEquals(latest, service.latestCompletedRun().get());
    }

    @Test
    void preservesThePreviousSnapshotWhenProductionFails() {
        RadarHotspotProductionPipeline pipeline = mock(RadarHotspotProductionPipeline.class);
        RadarRefreshRunRepository runs = mock(RadarRefreshRunRepository.class);
        RadarSnapshotProjectionService snapshots = mock(RadarSnapshotProjectionService.class);
        when(pipeline.run(any(), any(), any())).thenThrow(new IllegalStateException("生产失败"));
        RadarHotspotRefreshService service = new RadarHotspotRefreshService(pipeline, runs, snapshots, Runnable::run, Clock.systemDefaultZone());

        assertTrue(service.requestScheduledRefresh());

        verify(snapshots, never()).prewarm(any(), any());
    }

    @Test
    void publishesOnlyAfterTheCompletedResultHasBeenPrewarmed() {
        RadarHotspotProductionPipeline pipeline = mock(RadarHotspotProductionPipeline.class);
        RadarRefreshRunRepository runs = mock(RadarRefreshRunRepository.class);
        RadarSnapshotProjectionService snapshots = mock(RadarSnapshotProjectionService.class);
        RadarRefreshRun run = new RadarRefreshRun(); run.setCompletedAt(java.time.LocalDateTime.of(2026, 8, 6, 10, 0));
        RadarHotspotProductionPipeline.ProductionResult result = new RadarHotspotProductionPipeline.ProductionResult(run,
                new NewsFeedSnapshot(java.util.Collections.emptyList(), java.util.Collections.emptyList(), run.getCompletedAt(), 0),
                java.util.Collections.emptyList());
        when(pipeline.run(any(), any(), any())).thenReturn(result);
        when(snapshots.prewarm(result.getEvents(), result.getRun())).thenReturn(true);
        RadarHotspotRefreshService service = new RadarHotspotRefreshService(pipeline, runs, snapshots,
                Runnable::run, Clock.systemDefaultZone());

        assertTrue(service.requestScheduledRefresh());

        verify(snapshots).prewarm(result.getEvents(), result.getRun());
    }

    @Test
    void publishesTopTwentyInterpretationsOnlyAfterSnapshotIsVisible() {
        RadarHotspotProductionPipeline pipeline = mock(RadarHotspotProductionPipeline.class);
        RadarRefreshRunRepository runs = mock(RadarRefreshRunRepository.class);
        RadarSnapshotProjectionService snapshots = mock(RadarSnapshotProjectionService.class);
        RadarInterpretationBatchPublisher publisher = mock(RadarInterpretationBatchPublisher.class);
        RadarRefreshRun run = new RadarRefreshRun();
        run.setRunKey("radar-run-1");
        run.setCompletedAt(java.time.LocalDateTime.of(2026, 8, 12, 10, 0));
        List<RadarEvent> events = new ArrayList<RadarEvent>();
        for (long id = 1; id <= 25; id++) {
            RadarEvent event = new RadarEvent(); event.setId(id); events.add(event);
        }
        RadarHotspotProductionPipeline.ProductionResult result = new RadarHotspotProductionPipeline.ProductionResult(run,
                new NewsFeedSnapshot(java.util.Collections.emptyList(), java.util.Collections.emptyList(), run.getCompletedAt(), 0), events);
        when(pipeline.run(any(), any(), any())).thenReturn(result);
        when(snapshots.prewarm(events, run)).thenReturn(true);
        RadarHotspotRefreshService service = new RadarHotspotRefreshService(pipeline, runs, snapshots, publisher,
                Runnable::run, Clock.systemDefaultZone());

        assertTrue(service.requestScheduledRefresh());

        org.mockito.InOrder order = inOrder(snapshots, publisher);
        order.verify(snapshots).prewarm(events, run);
        order.verify(publisher).publish(org.mockito.ArgumentMatchers.argThat((RadarInterpretationBatchMessage message) ->
                "radar-run-1".equals(message.getRunKey()) && message.getEventIds().size() == 20
                        && message.getEventIds().get(0).equals(1L) && message.getEventIds().get(19).equals(20L)));
    }

    @Test
    void skipsInterpretationMessageWhenSnapshotPublicationFails() {
        RadarHotspotProductionPipeline pipeline = mock(RadarHotspotProductionPipeline.class);
        RadarRefreshRunRepository runs = mock(RadarRefreshRunRepository.class);
        RadarSnapshotProjectionService snapshots = mock(RadarSnapshotProjectionService.class);
        RadarInterpretationBatchPublisher publisher = mock(RadarInterpretationBatchPublisher.class);
        RadarRefreshRun run = new RadarRefreshRun(); run.setRunKey("radar-run-2");
        RadarHotspotProductionPipeline.ProductionResult result = new RadarHotspotProductionPipeline.ProductionResult(run,
                new NewsFeedSnapshot(java.util.Collections.emptyList(), java.util.Collections.emptyList(), java.time.LocalDateTime.now(), 0),
                java.util.Collections.emptyList());
        when(pipeline.run(any(), any(), any())).thenReturn(result);
        when(snapshots.prewarm(result.getEvents(), run)).thenReturn(false);
        RadarHotspotRefreshService service = new RadarHotspotRefreshService(pipeline, runs, snapshots, publisher,
                Runnable::run, Clock.systemDefaultZone());

        assertTrue(service.requestScheduledRefresh());

        verify(publisher, never()).publish(any());
    }

    @Test
    void keepsCompletedRankingVisibleWhenKafkaPublicationFails() {
        RadarHotspotProductionPipeline pipeline = mock(RadarHotspotProductionPipeline.class);
        RadarRefreshRunRepository runs = mock(RadarRefreshRunRepository.class);
        RadarSnapshotProjectionService snapshots = mock(RadarSnapshotProjectionService.class);
        RadarInterpretationBatchPublisher publisher = mock(RadarInterpretationBatchPublisher.class);
        RadarRefreshRun run = new RadarRefreshRun(); run.setRunKey("radar-run-3");
        RadarEvent event = new RadarEvent(); event.setId(10L);
        RadarHotspotProductionPipeline.ProductionResult result = new RadarHotspotProductionPipeline.ProductionResult(run,
                new NewsFeedSnapshot(java.util.Collections.emptyList(), java.util.Collections.emptyList(), java.time.LocalDateTime.now(), 0),
                java.util.Collections.singletonList(event));
        when(pipeline.run(any(), any(), any())).thenReturn(result);
        when(snapshots.prewarm(result.getEvents(), run)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("Kafka unavailable")).when(publisher).publish(any());
        RadarHotspotRefreshService service = new RadarHotspotRefreshService(pipeline, runs, snapshots, publisher,
                Runnable::run, Clock.systemDefaultZone());

        assertTrue(service.requestScheduledRefresh());

        verify(snapshots).prewarm(result.getEvents(), run);
        assertFalse(service.isRunning());
    }
}
