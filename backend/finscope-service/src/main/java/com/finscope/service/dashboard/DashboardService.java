package com.finscope.service.dashboard;

import com.finscope.dao.brief.BriefRepository;
import com.finscope.dao.fetch.FetchRunRepository;
import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.fetch.FetchRun;
import com.finscope.service.article.ArticleQueryService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {
    private final SourceRepository sourceRepository;
    private final ArticleQueryService articleQueryService;
    private final BriefRepository briefRepository;
    private final FetchRunRepository fetchRunRepository;
    private final DashboardHotspotRankingService hotspotRankings;

    public DashboardService(SourceRepository sourceRepository,
                            ArticleQueryService articleQueryService,
                            BriefRepository briefRepository,
                            FetchRunRepository fetchRunRepository,
                            DashboardHotspotRankingService hotspotRankings) {
        this.sourceRepository = sourceRepository;
        this.articleQueryService = articleQueryService;
        this.briefRepository = briefRepository;
        this.fetchRunRepository = fetchRunRepository;
        this.hotspotRankings = hotspotRankings;
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("sourceCount", sourceRepository.findAll().size());
        result.put("articleCount", articleQueryService.countAll());
        result.put("briefCount", briefRepository.findAll().size());
        List<FetchRun> runs = fetchRunRepository.latest(5);
        result.put("latestFetchRuns", runs);
        result.put("hotspotRankings", hotspotRankings.rankings());
        return result;
    }
}
