package com.finscope.service.research;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.research.ContentIdeaRepository;
import com.finscope.dao.research.EventClusterRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.agent.AgentActionFingerprint;
import com.finscope.domain.agent.AgentNodeResult;
import com.finscope.domain.agent.AgentRunContext;
import com.finscope.domain.brief.Brief;
import com.finscope.domain.fetch.FetchRun;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchRunPlan;
import com.finscope.domain.research.ResearchRunPlanStep;
import com.finscope.domain.research.SourceProfile;
import com.finscope.domain.research.ThemeProfile;
import com.finscope.domain.source.Source;
import com.finscope.service.agent.ActionFingerprintService;
import com.finscope.service.agent.AgentHarness;
import com.finscope.service.agent.AgentTraceService;
import com.finscope.service.brief.BriefService;
import com.finscope.service.fetch.FetchService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

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
    @Resource
    private AgentHarness agentHarness;
    @Resource
    private ActionFingerprintService actionFingerprintService;
    @Resource
    private AgentTraceService agentTraceService;
    @Resource
    private ResearchRunPlanService researchRunPlanService;
    @Resource(name = "researchTaskExecutor")
    private Executor researchTaskExecutor;

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
        List<ResearchRunPlanStep> planSteps = researchRunPlanService.initializeDefaultPlan(saved.getId(), plannedSources.size());
        startStep(planSteps, ResearchRunPlanService.STEP_PLAN_SOURCES);
        if (plannedSources.isEmpty()) {
            saved = failWithoutPlannedSources(saved, planSteps);
            ResearchRunPlan plan = new ResearchRunPlan();
            plan.setRun(saved);
            plan.setPlannedSources(plannedSources);
            plan.setPlanSteps(planSteps);
            return plan;
        }
        completeStep(planSteps, ResearchRunPlanService.STEP_PLAN_SOURCES,
                "plannedSources=" + plannedSources.size(), plannedSources.size());
        scheduleExecution(saved, plannedSources, planSteps);
        ResearchRunPlan plan = new ResearchRunPlan();
        plan.setRun(saved);
        plan.setPlannedSources(plannedSources);
        plan.setPlanSteps(planSteps);
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

    private void scheduleExecution(ResearchRun run, List<SourceProfile> plannedSources, List<ResearchRunPlanStep> planSteps) {
        try {
            researchTaskExecutor.execute(() -> execute(run, plannedSources, planSteps));
        } catch (RuntimeException ex) {
            failScheduledRun(run, planSteps, ex);
        }
    }

    private void failScheduledRun(ResearchRun run, List<ResearchRunPlanStep> planSteps, RuntimeException ex) {
        run.setStatus(ResearchEnums.RUN_STATUS_FAILED);
        run.setSummary(resultSummary(run));
        run.setErrorMessage(ex.getMessage());
        researchRunRepository.updateResult(run);
        ResearchRunPlanStep fetchStep = researchRunPlanService.findStep(planSteps, ResearchRunPlanService.STEP_FETCH_SOURCES);
        failStep(fetchStep, "RESEARCH_EXECUTOR_REJECTED", ex.getMessage());
        agentRunRepository.record(run.getId(), null, null, "research-orchestrate", "FAILED",
                "themes=" + String.join(",", run.getThemeCodes()) + ", date=" + run.getRunDate(),
                null, ex.getMessage(), 0L);
    }

    private ResearchRun execute(ResearchRun run, List<SourceProfile> plannedSources, List<ResearchRunPlanStep> planSteps) {
        long start = System.currentTimeMillis();
        int articleBefore = articleRepository.countAll();
        int eventBefore = eventClusterRepository.countAll();
        int evidenceBefore = evidenceItemRepository.countAll();
        int learningBefore = learningTaskRepository.countAll();
        int ideaBefore = contentIdeaRepository.countAll();
        int fetchedSources = 0;
        List<String> errors = new ArrayList<String>();
        ResearchRunPlanStep currentStep = null;
        try {
            ResearchRunContext.setCurrentRunId(run.getId());
            AgentRunContext context = ResearchRunContext.currentContext();
            currentStep = startStep(planSteps, ResearchRunPlanService.STEP_FETCH_SOURCES);
            for (SourceProfile plannedSource : plannedSources) {
                Long sourceId = plannedSource.getSourceId();
                if (sourceId == null) {
                    continue;
                }
                long sourceStart = System.currentTimeMillis();
                AgentActionFingerprint fingerprint = actionFingerprintService.sourceFetch(sourceId);
                AgentNodeResult<FetchRun> nodeResult = agentHarness.runNode(context, fingerprint,
                        ctx -> {
                            FetchRun fetchRun = fetchService.fetch(sourceId);
                            return AgentNodeResult.success(fetchRun,
                                    sourceFetchInput(sourceId, plannedSource),
                                    sourceFetchOutput(fetchRun),
                                    fetchRun.getSuccessCount());
                        });
                FetchRun fetchRun = nodeResult.getValue();
                if (fetchRun != null && "SUCCESS".equals(fetchRun.getStatus())) {
                    fetchedSources++;
                } else if (fetchRun != null) {
                    errors.add(fetchRun.getSourceName() + ": " + fetchRun.getErrorMessage());
                } else if (!"SUCCESS".equals(nodeResult.getStatus())) {
                    errors.add(plannedSource.getSourceName() + ": " + nodeResult.getErrorMessage());
                }
                agentTraceService.recordNode(null, null, context, fingerprint, nodeResult,
                        System.currentTimeMillis() - sourceStart, sourceFetchMetadata(sourceId));
            }

            completeStep(planSteps, ResearchRunPlanService.STEP_FETCH_SOURCES,
                    "fetchedSources=" + fetchedSources + ", errors=" + errors.size(), fetchedSources);
            currentStep = startStep(planSteps, ResearchRunPlanService.STEP_CLASSIFY_EVENTS);
            completeStep(planSteps, ResearchRunPlanService.STEP_CLASSIFY_EVENTS,
                    "events=" + delta(eventBefore, eventClusterRepository.countAll()),
                    delta(eventBefore, eventClusterRepository.countAll()));
            currentStep = startStep(planSteps, ResearchRunPlanService.STEP_EXTRACT_EVIDENCE);
            completeStep(planSteps, ResearchRunPlanService.STEP_EXTRACT_EVIDENCE,
                    "evidence=" + delta(evidenceBefore, evidenceItemRepository.countAll()),
                    delta(evidenceBefore, evidenceItemRepository.countAll()));
            currentStep = startStep(planSteps, ResearchRunPlanService.STEP_COMPOSE_BRIEF);
            Brief brief = briefService.generate(run.getRunDate());
            completeStep(planSteps, ResearchRunPlanService.STEP_COMPOSE_BRIEF,
                    "briefDate=" + brief.getBriefDate(), 1);
            currentStep = startStep(planSteps, ResearchRunPlanService.STEP_SUMMARIZE_RUN);
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
            completeStep(planSteps, ResearchRunPlanService.STEP_SUMMARIZE_RUN, run.getSummary(), 1);
            agentRunRepository.record(run.getId(), null, null, "research-orchestrate", run.getStatus(),
                    "themes=" + String.join(",", run.getThemeCodes()) + ", date=" + run.getRunDate(),
                    run.getSummary(), run.getErrorMessage(), System.currentTimeMillis() - start);
            ResearchRun updated = researchRunRepository.updateResult(run);
            return updated;
        } catch (Exception ex) {
            run.setFetchedSourceCount(fetchedSources);
            run.setStatus(ResearchEnums.RUN_STATUS_FAILED);
            run.setSummary(resultSummary(run));
            run.setErrorMessage(ex.getMessage());
            ResearchRun updated = researchRunRepository.updateResult(run);
            if (currentStep != null) {
                failStep(currentStep, "UNKNOWN", ex.getMessage());
            }
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

    private String sourceFetchInput(Long sourceId, SourceProfile plannedSource) {
        return "sourceId=" + sourceId + ", name=" + plannedSource.getSourceName();
    }

    private String sourceFetchOutput(FetchRun fetchRun) {
        return "success=" + fetchRun.getSuccessCount() + ", duplicate=" + fetchRun.getDuplicateCount();
    }

    private String sourceFetchMetadata(Long sourceId) {
        return "{\"sourceId\":" + sourceId + "}";
    }

    private ResearchRun failWithoutPlannedSources(ResearchRun run, List<ResearchRunPlanStep> planSteps) {
        String message = "No planned sources matched themes=" + String.join(",", run.getThemeCodes())
                + ". Add source tags such as 市场/宏观/AI/公司 or enable matching sources.";
        ResearchRunPlanStep planStep = researchRunPlanService.findStep(planSteps, ResearchRunPlanService.STEP_PLAN_SOURCES);
        researchRunPlanService.fail(planStep, "NO_PLANNED_SOURCES", message);
        skipStep(planSteps, ResearchRunPlanService.STEP_FETCH_SOURCES, "NO_PLANNED_SOURCES");
        skipStep(planSteps, ResearchRunPlanService.STEP_CLASSIFY_EVENTS, "NO_PLANNED_SOURCES");
        skipStep(planSteps, ResearchRunPlanService.STEP_EXTRACT_EVIDENCE, "NO_PLANNED_SOURCES");
        skipStep(planSteps, ResearchRunPlanService.STEP_COMPOSE_BRIEF, "NO_PLANNED_SOURCES");
        skipStep(planSteps, ResearchRunPlanService.STEP_SUMMARIZE_RUN, "NO_PLANNED_SOURCES");
        run.setFetchedSourceCount(0);
        run.setArticleCount(0);
        run.setEventCount(0);
        run.setEvidenceCount(0);
        run.setLearningTaskCount(0);
        run.setContentIdeaCount(0);
        run.setStatus(ResearchEnums.RUN_STATUS_FAILED);
        run.setSummary("No research sources were planned for this run.");
        run.setErrorMessage(message);
        ResearchRun updated = researchRunRepository.updateResult(run);
        agentRunRepository.record(run.getId(), null, null, "research-orchestrate", "FAILED",
                "themes=" + String.join(",", run.getThemeCodes()) + ", date=" + run.getRunDate(),
                null, message, 0L);
        return updated;
    }

    private ResearchRunPlanStep startStep(List<ResearchRunPlanStep> planSteps, String stepId) {
        ResearchRunPlanStep step = researchRunPlanService.findStep(planSteps, stepId);
        researchRunPlanService.start(step);
        return step;
    }

    private void completeStep(List<ResearchRunPlanStep> planSteps, String stepId, String outputSummary, int progressDelta) {
        ResearchRunPlanStep step = researchRunPlanService.findStep(planSteps, stepId);
        researchRunPlanService.complete(step, outputSummary, progressDelta);
    }

    private void failStep(ResearchRunPlanStep step, String errorType, String errorMessage) {
        researchRunPlanService.fail(step, errorType, errorMessage);
    }

    private void skipStep(List<ResearchRunPlanStep> planSteps, String stepId, String reason) {
        ResearchRunPlanStep step = researchRunPlanService.findStep(planSteps, stepId);
        researchRunPlanService.skip(step, reason);
    }
}
