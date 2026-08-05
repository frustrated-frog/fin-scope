package com.finscope.service.radar;

import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.service.dedupe.FingerprintService;
import com.finscope.service.news.NewsFeedService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchRadarSnapshotReadTest {
    @Test
    void pageRefreshRequestsBackgroundProductionAndDoesNotFetchInTheHttpPath() {
        NewsFeedService news = mock(NewsFeedService.class);
        RadarRepository repository = mock(RadarRepository.class);
        WatchlistRepository watchlist = mock(WatchlistRepository.class);
        RadarHotspotRefreshService refresh = mock(RadarHotspotRefreshService.class);
        when(repository.findRanked("ALL", false, 50)).thenReturn(Collections.emptyList());

        ResearchRadarService service = new ResearchRadarService(news, repository,
                new RadarClusteringService(new RadarTextAnalyzer(new FingerprintService())),
                new RadarPriorityService(), watchlist, refresh,
                Clock.fixed(Instant.parse("2026-08-05T02:00:00Z"), ZoneId.of("Asia/Shanghai")));

        service.load("ALL", false, 20);

        verify(refresh).requestRefresh();
        verify(news, never()).load(anyString(), anyInt());
    }
}
