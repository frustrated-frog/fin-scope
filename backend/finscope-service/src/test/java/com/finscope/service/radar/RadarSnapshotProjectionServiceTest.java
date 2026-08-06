package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.service.cache.ViewRevisionService;
import com.finscope.service.cache.ViewSnapshotCacheService;
import com.finscope.service.dashboard.DashboardHotspotRankingService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RadarSnapshotProjectionServiceTest {
    @Test
    void prewarmsRadarAndDashboardSnapshotsFromTheCompletedProductionEvents() {
        ViewSnapshotCacheService snapshots = mock(ViewSnapshotCacheService.class);
        ViewRevisionService revisions = mock(ViewRevisionService.class);
        DashboardHotspotRankingService rankings = mock(DashboardHotspotRankingService.class);
        RadarSnapshotProjectionService service = new RadarSnapshotProjectionService(snapshots, revisions, rankings);
        RadarRefreshRun run = run();
        when(snapshots.nextRevision("radar")).thenReturn(4L);
        when(snapshots.nextRevision("dashboard")).thenReturn(9L);
        when(snapshots.write(eq("radar"), eq(4L), eq(RadarSnapshotProjectionService.DEFAULT_RADAR_VARIANT), any(), any())).thenReturn(true);
        when(snapshots.write(eq("dashboard"), eq(9L), eq(RadarSnapshotProjectionService.HOTSPOT_VARIANT), any(), any())).thenReturn(true);

        assertTrue(service.prewarm(Arrays.asList(event(1L, "FINANCE"), event(2L, "TECHNOLOGY")), run));

        verify(rankings).rankings(any());
        verify(revisions).publish("radar", 4L, run.getCompletedAt());
        verify(revisions).publish("dashboard", 9L, run.getCompletedAt());
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
