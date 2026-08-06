package com.finscope.service.dashboard;

import com.finscope.dao.brief.BriefRepository;
import com.finscope.dao.fetch.FetchRunRepository;
import com.finscope.dao.source.SourceRepository;
import com.finscope.service.article.ArticleQueryService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {
    @Test
    void leavesHotspotRankingsToTheDedicatedSnapshotEndpoint() {
        SourceRepository sources = mock(SourceRepository.class);
        ArticleQueryService articles = mock(ArticleQueryService.class);
        BriefRepository briefs = mock(BriefRepository.class);
        FetchRunRepository fetchRuns = mock(FetchRunRepository.class);
        when(sources.findAll()).thenReturn(Collections.emptyList());
        when(articles.countAll()).thenReturn(0);
        when(briefs.findAll()).thenReturn(Collections.emptyList());
        when(fetchRuns.latest(5)).thenReturn(Collections.emptyList());

        DashboardService service = new DashboardService(sources, articles, briefs, fetchRuns);
        Map<String, Object> summary = service.summary();

        assertFalse(summary.containsKey("hotspotRankings"));
    }
}
