package com.finscope.service.research;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.research.ContentIdeaRepository;
import com.finscope.dao.research.EventClusterRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.brief.Brief;
import com.finscope.domain.fetch.FetchRun;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchRunPlan;
import com.finscope.domain.research.SourceProfile;
import com.finscope.domain.research.ThemeProfile;
import com.finscope.domain.source.Source;
import com.finscope.service.brief.BriefService;
import com.finscope.service.fetch.FetchService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ResearchService {
    @Resource
    private ThemeProfileService themeProfileService;
    @Resource
    private SourcePlanner sourcePlanner;
    @Resource
    private SourceRepository sourceRepository;
    @Resource
    private ResearchRunRepository researchRunRepository;
    @Resource
    private FetchService fetchService;
    @Resource
    private BriefService briefService;
    @Resource
    private ArticleRepository articleRepository;
    @Resource
    private EventClusterRepository eventClusterRepository;
    @Resource
    private EvidenceItemRepository evidenceItemRepository;
    @Resource
    private LearningTaskRepository learningTaskRepository;
    @Resource
    private ContentIdeaRepository contentIdeaRepository;
    @Resource
    private AgentRunRepository agentRunRepository;

    public ResearchRunPlan createRun(LocalDate runDate,
                                     List<String> themeCodes,
                                     Integer maxSourcesPerTheme,
                                     Boolean includeDisabled) {
        LocalDate actualRunDate = runDate == null ? LocalDate.now() : runDate;
        int actualMaxSources = maxSourcesPerTheme == null ? 3 : maxSourcesPerTheme;
        boolean actualIncludeDisabled = includeDisabled != null && includeDisabled;

        List<ThemeProfile> themes = themeProfileService.getRequired(themeCodes);
        List<SourceProfile> plannedSources = sourcePlanner.plan(
                actualRunDate,
                themeCodes,
                actualMaxSources,
                actualIncludeDisabled,
                toProfiles(sourceRepository.findAll()));

        ResearchRun run = new ResearchRun();
        run.setRunDate(actualRunDate);
        run.setThemeCodes(extractCodes(themes));
        run.setSourceCount(plannedSources.size());
        run.setFetchedSourceCount(0);
        run.setArticleCount(0);
        run.setEventCount(0);
        run.setEvidenceCount(0);
        run.setLearningTaskCount(0);
        run.setContentIdeaCount(0);
        run.setStatus(ResearchEnums.RUN_STATUS_RUNNING);
        run.setSummary(buildSummary(themes, plannedSources));
        run.setErrorMessage(null);

        ResearchRun saved = researchRunRepository.save(run);
        researchRunRepository.replaceSources(saved.getId(), plannedSources);
        saved = execute(saved, plannedSources);
        ResearchRunPlan plan = new ResearchRunPlan();
        plan.setRun(saved);
        plan.setPlannedSources(plannedSources);
        return plan;
    }

    public List<ResearchRun> listRuns() {
        return researchRunRepository.findAll();
    }

    public ResearchRun detail(Long id) {
        return researchRunRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Research run not found: " + id));
    }

    public List<SourceProfile> plannedSources(Long id) {
        detail(id);
        return researchRunRepository.findSourcesByRunId(id);
    }

    private ResearchRun execute(ResearchRun run, List<SourceProfile> plannedSources) {
        long start = System.currentTimeMillis();
        int articleBefore = articleRepository.countAll();
        int eventBefore = eventClusterRepository.countAll();
        int evidenceBefore = evidenceItemRepository.countAll();
        int learningBefore = learningTaskRepository.countAll();
        int ideaBefore = contentIdeaRepository.countAll();
        int fetchedSources = 0;
        List<String> errors = new ArrayList<String>();
        try {
            ResearchRunContext.setCurrentRunId(run.getId());
            for (SourceProfile plannedSource : plannedSources) {
                Long sourceId = plannedSource.getSourceId();
                if (sourceId == null) {
                    continue;
                }
                long sourceStart = System.currentTimeMillis();
                FetchRun fetchRun = fetchService.fetch(sourceId);
                if ("SUCCESS".equals(fetchRun.getStatus())) {
                    fetchedSources++;
                } else {
                    errors.add(fetchRun.getSourceName() + ": " + fetchRun.getErrorMessage());
                }
                agentRunRepository.record(run.getId(), null, null, "source-fetch", fetchRun.getStatus(),
                        "sourceId=" + sourceId + ", name=" + plannedSource.getSourceName(),
                        "success=" + fetchRun.getSuccessCount() + ", duplicate=" + fetchRun.getDuplicateCount(),
                        fetchRun.getErrorMessage(), System.currentTimeMillis() - sourceStart);
            }

            Brief brief = briefService.generate(run.getRunDate());
            run.setFetchedSourceCount(fetchedSources);
            run.setArticleCount(delta(articleBefore, articleRepository.countAll()));
            run.setEventCount(delta(eventBefore, eventClusterRepository.countAll()));
            run.setEvidenceCount(delta(evidenceBefore, evidenceItemRepository.countAll()));
            run.setLearningTaskCount(delta(learningBefore, learningTaskRepository.countAll()));
            run.setContentIdeaCount(delta(ideaBefore, contentIdeaRepository.countAll()));
            run.setBriefDate(brief.getBriefDate());
            run.setStatus(errors.isEmpty()
                    ? ResearchEnums.RUN_STATUS_COMPLETED
                    : ResearchEnums.RUN_STATUS_PARTIAL_SUCCESS);
            run.setSummary(resultSummary(run));
            run.setErrorMessage(errors.isEmpty() ? null : String.join("; ", errors));
            ResearchRun updated = researchRunRepository.updateResult(run);
            agentRunRepository.record(run.getId(), null, null, "research-orchestrate", updated.getStatus(),
                    "themes=" + String.join(",", run.getThemeCodes()) + ", date=" + run.getRunDate(),
                    updated.getSummary(), updated.getErrorMessage(), System.currentTimeMillis() - start);
            return updated;
        } catch (Exception ex) {
            run.setFetchedSourceCount(fetchedSources);
            run.setStatus(ResearchEnums.RUN_STATUS_FAILED);
            run.setSummary(resultSummary(run));
            run.setErrorMessage(ex.getMessage());
            ResearchRun updated = researchRunRepository.updateResult(run);
            agentRunRepository.record(run.getId(), null, null, "research-orchestrate", "FAILED",
                    "themes=" + String.join(",", run.getThemeCodes()) + ", date=" + run.getRunDate(),
                    null, ex.getMessage(), System.currentTimeMillis() - start);
            return updated;
        } finally {
            ResearchRunContext.clear();
        }
    }

    private List<SourceProfile> toProfiles(List<Source> sources) {
        List<SourceProfile> profiles = new ArrayList<SourceProfile>();
        for (Source source : sources) {
            profiles.add(SourceProfile.from(source));
        }
        return profiles;
    }

    private List<String> extractCodes(List<ThemeProfile> themes) {
        List<String> codes = new ArrayList<String>();
        for (ThemeProfile theme : themes) {
            codes.add(theme.getCode());
        }
        return codes;
    }

    private String buildSummary(List<ThemeProfile> themes, List<SourceProfile> plannedSources) {
        List<String> themeNames = new ArrayList<String>();
        for (ThemeProfile theme : themes) {
            themeNames.add(theme.getName());
        }
        return "Planned " + plannedSources.size() + " sources for themes: " + String.join(", ", themeNames);
    }

    private int delta(int before, int after) {
        return Math.max(0, after - before);
    }

    private String resultSummary(ResearchRun run) {
        return "sources=" + value(run.getSourceCount())
                + ", fetched=" + value(run.getFetchedSourceCount())
                + ", articles=" + value(run.getArticleCount())
                + ", events=" + value(run.getEventCount())
                + ", evidence=" + value(run.getEvidenceCount())
                + ", learningTasks=" + value(run.getLearningTaskCount())
                + ", contentIdeas=" + value(run.getContentIdeaCount())
                + ", briefDate=" + (run.getBriefDate() == null ? "-" : run.getBriefDate());
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
