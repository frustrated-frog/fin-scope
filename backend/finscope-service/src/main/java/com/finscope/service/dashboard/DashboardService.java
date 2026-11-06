package com.finscope.service.dashboard;

import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.brief.BriefRepository;
import com.finscope.dao.fetch.FetchRunRepository;
import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.fetch.FetchRun;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {
    @Resource
    private SourceRepository sourceRepository;
    @Resource
    private ArticleRepository articleRepository;
    @Resource
    private BriefRepository briefRepository;
    @Resource
    private FetchRunRepository fetchRunRepository;

    public Map<String, Object> summary() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("sourceCount", sourceRepository.findAll().size());
        result.put("articleCount", articleRepository.countAll());
        result.put("briefCount", briefRepository.findAll().size());
        List<FetchRun> runs = fetchRunRepository.latest(5);
        result.put("latestFetchRuns", runs);
        return result;
    }
}
