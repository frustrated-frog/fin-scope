package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.service.cache.ViewRevisionService;
import com.finscope.service.cache.ViewSnapshotCacheService;
import com.finscope.service.dashboard.DashboardHotspotRankingService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class RadarSnapshotProjectionServiceTest {
    @Test
    void prewarmsRadarAndDashboardSnapshotsFromTheCompletedProductionEvents() {
        ViewSnapshotCacheService snapshots = mock(ViewSnapshotCacheService.class);
        ViewRevisionService revisions = mock(ViewRevisionService.class);
        DashboardHotspotRankingService rankings = mock(DashboardHotspotRankingService.class);
        RadarEventInterpretationService interpretations = mock(RadarEventInterpretationService.class);
        RadarEventWorkspaceService workspace = mock(RadarEventWorkspaceService.class);
        RadarSnapshotProjectionService service = new RadarSnapshotProjectionService(snapshots, revisions, rankings,
                interpretations, workspace);
        RadarRefreshRun run = run();
        when(snapshots.nextRevision("radar")).thenReturn(4L);
        when(snapshots.nextRevision("dashboard")).thenReturn(9L);
        when(snapshots.write(eq("radar"), eq(4L), eq(RadarSnapshotProjectionService.DEFAULT_RADAR_VARIANT), any(), any())).thenReturn(true);
        when(snapshots.write(eq("dashboard"), eq(9L), eq(RadarSnapshotProjectionService.HOTSPOT_VARIANT), any(), any())).thenReturn(true);
        Map<String, Long> revisionsToPublish = new LinkedHashMap<String, Long>();
        revisionsToPublish.put("radar", 4L); revisionsToPublish.put("dashboard", 9L);
        when(revisions.publishBatch(revisionsToPublish, run.getCompletedAt())).thenReturn(true);
        RadarEventInterpretation interpretation = new RadarEventInterpretation();
        interpretation.setStatus("COMPLETED");
        RadarEventWorkspace.Summary summary = new RadarEventWorkspace.Summary();
        summary.setFollowed(true);
        when(interpretations.latestByEventIds(Arrays.asList(1L, 2L)))
                .thenReturn(Collections.singletonMap(1L, interpretation));
        when(workspace.summaries(Arrays.asList(1L, 2L)))
                .thenReturn(Collections.singletonMap(1L, summary));

        assertTrue(service.prewarm(Arrays.asList(event(1L, "FINANCE"), event(2L, "TECHNOLOGY")), run));

        verify(rankings).rankings(any());
        verify(workspace, times(2)).reconcileRead(any(), any());
        verify(workspace).createChangeNotifications(any(), any());
        verify(revisions).publishBatch(revisionsToPublish, run.getCompletedAt());
        ArgumentCaptor<Object> radarView = ArgumentCaptor.forClass(Object.class);
        verify(snapshots).write(eq("radar"), eq(4L), eq(RadarSnapshotProjectionService.DEFAULT_RADAR_VARIANT),
                radarView.capture(), any());
        ResearchRadarView projected = (ResearchRadarView) radarView.getValue();
        assertTrue(projected.getEvents().get(0).isFollowed());
        assertTrue("COMPLETED".equals(projected.getEvents().get(0).getInterpretationStatus()));
    }

    @Test
    void doesNotReportSuccessWhenTheAtomicRevisionActivationFails() {
        ViewSnapshotCacheService snapshots = mock(ViewSnapshotCacheService.class);
        ViewRevisionService revisions = mock(ViewRevisionService.class);
        DashboardHotspotRankingService rankings = mock(DashboardHotspotRankingService.class);
        RadarSnapshotProjectionService service = new RadarSnapshotProjectionService(snapshots, revisions, rankings,
                null, null);
        RadarRefreshRun run = run();
        when(snapshots.nextRevision("radar")).thenReturn(4L);
        when(snapshots.nextRevision("dashboard")).thenReturn(9L);
        when(snapshots.write(eq("radar"), eq(4L), any(), any(), any())).thenReturn(true);
        when(snapshots.write(eq("dashboard"), eq(9L), any(), any(), any())).thenReturn(true);

        assertFalse(service.prewarm(Collections.singletonList(event(1L, "FINANCE")), run));
    }

    private RadarRefreshRun run() {
        RadarRefreshRun run = new RadarRefreshRun();
        run.setStatus("SUCCESS");
        run.setCompletedAt(LocalDateTime.of(2026, 8, 6, 12, 0));
        run.setSourceCount(2);
        run.setSignalCount(3);
        run.setEventCount(2);
        return run;
    }

    private RadarEvent event(Long id, String category) {
        RadarEvent event = new RadarEvent();
        event.setId(id); event.setCanonicalTitle(category + " event"); event.setStatus("ACTIVE");
        event.setDashboardCategory(category); event.setPriorityScore(80); event.setHotspotScore(90);
        event.setLastSeenAt(LocalDateTime.of(2026, 8, 6, 12, 0));
        return event;
    }
}
