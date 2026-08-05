package com.finscope.service.radar;

import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.service.dedupe.FingerprintService;
import com.finscope.service.news.NewsFeedService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResearchRadarProductionStatusTest {
    @Test
    void projectsLatestBatchAndRunningStateWithoutRawFailureDetails() {
        NewsFeedService news = mock(NewsFeedService.class);
        RadarRepository repository = mock(RadarRepository.class);
        WatchlistRepository watchlist = mock(WatchlistRepository.class);
        RadarHotspotRefreshService refresh = mock(RadarHotspotRefreshService.class);
        RadarRefreshRun latest = new RadarRefreshRun();
        latest.setStatus("SUCCESS"); latest.setCompletedAt(LocalDateTime.of(2026, 8, 5, 10, 0));
        latest.setSourceCount(3); latest.setSignalCount(42); latest.setEventCount(12);
        latest.setError("raw upstream stack trace must not be returned");
        when(repository.findRanked("ALL", false, 50)).thenReturn(Collections.emptyList());
        when(refresh.latestCompletedRun()).thenReturn(Optional.of(latest));
        when(refresh.isRunning()).thenReturn(true);

        ResearchRadarService service = new ResearchRadarService(news, repository,
                new RadarClusteringService(new RadarTextAnalyzer(new FingerprintService())),
                new RadarPriorityService(), watchlist, refresh,
                Clock.fixed(Instant.parse("2026-08-05T02:00:00Z"), ZoneId.of("Asia/Shanghai")));

        ResearchRadarView view = service.loadStored("ALL", false, 20, "ALL");

        assertTrue(view.getProductionStatus().isRunning());
        assertEquals("SUCCESS", view.getProductionStatus().getStatus());
        assertEquals(12, view.getProductionStatus().getEventCount());
        assertEquals(latest.getCompletedAt(), view.getRefreshedAt());
        assertTrue(view.getProductionStatus().getWarning() == null ||
                !view.getProductionStatus().getWarning().contains("stack trace"));
    }
}
