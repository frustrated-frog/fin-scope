package com.finscope.service.research;

import com.finscope.common.exception.BusinessConflictException;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.research.ContentIdeaRepository;
import com.finscope.dao.research.EventClusterRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.dao.research.ResearchSearchEvidenceRepository;
import com.finscope.dao.research.ResearchThesisRepository;
import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.agent.AgentActionFingerprint;
import com.finscope.domain.agent.AgentNodeResult;
import com.finscope.domain.agent.AgentRunContext;
import com.finscope.domain.fetch.FetchRun;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.research.ResearchMode;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchRunPlan;
import com.finscope.domain.research.ResearchRunPlanStep;
import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.SourceProfile;
import com.finscope.domain.research.ThemeProfile;
import com.finscope.domain.research.mission.ResearchMissionTask;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.domain.source.Source;
import com.finscope.service.agent.ActionFingerprintService;
import com.finscope.service.agent.AgentHarness;
import com.finscope.service.agent.AgentTraceService;
import com.finscope.service.fetch.FetchService;
import com.finscope.service.research.agent.ResearchAgentLoopResult;
import com.finscope.service.research.agent.ResearchAgentLoopService;
import com.finscope.service.research.report.ResearchReportService;
import com.finscope.service.research.mission.ResearchMissionService;
import com.finscope.service.research.runtime.ResearchRuntimeService;
import com.finscope.service.research.runtime.ResearchRuntimePolicy;
import com.finscope.service.research.runtime.RuntimeNodeStart;
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
    private ResearchThesisRepository researchThesisRepository;
    @Resource
    private FetchService fetchService;
    @Resource
    private ResearchReportService researchReportService;
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
    @Resource
    private ResearchRunOutputService researchRunOutputService;
    @Resource
    private ResearchSearchEvidenceRepository researchSearchEvidenceRepository;
    @Resource
    private ResearchRuntimeService researchRuntimeService;
    @Resource
    private ResearchMissionService researchMissionService;
    @Resource
    private ResearchAgentLoopService researchAgentLoopService;
    @Resource(name = "researchTaskExecutor")
    private Executor researchTaskExecutor;

    public ResearchRunPlan createRun(LocalDate runDate,
                                     List<String> themeCodes,
                                     Integer maxSourcesPerTheme,
                                     Boolean includeDisabled) {
        return createRun(null, runDate, themeCodes, maxSourcesPerTheme, includeDisabled, ResearchMode.DEEP);
    }

    public ResearchRunPlan createRun(Long thesisId,
                                     LocalDate runDate,
                                     List<String> themeCodes,
                                     Integer maxSourcesPerTheme,
                                     Boolean includeDisabled) {
        return createRun(thesisId, runDate, themeCodes, maxSourcesPerTheme, includeDisabled, ResearchMode.DEEP);
    }

    public ResearchRunPlan createRun(Long thesisId,
                                     LocalDate runDate,
                                     List<String> themeCodes,
                                     Integer maxSourcesPerTheme,
                                     Boolean includeDisabled,
                                     ResearchMode mode) {
        ResearchMode actualMode = ResearchMode.defaultIfNull(mode);
        ResearchThesis thesis = null;
        if (thesisId != null) {
            thesis = researchThesisRepository == null ? null : researchThesisRepository.findById(thesisId)
                    .orElseThrow(() -> new ResourceNotFoundException("研究命题不存在：" + thesisId));
        }
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
        run.setThesisId(thesisId);
        run.setMode(actualMode);
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
        int maxActions = ResearchRuntimePolicy.maxActionsFor(actualMode);
        researchRuntimeService.initialize(saved.getId(), maxActions);
        if (thesis != null) {
            researchMissionService.initializePending(saved, thesis, maxActions);
        }
        researchRunRepository.replaceSources(saved.getId(), plannedSources);
        List<ResearchRunPlanStep> planSteps = researchRunPlanService.initializeDefaultPlan(saved.getId(), plannedSources.size());
        startStep(planSteps, ResearchRunPlanService.STEP_PLAN_SOURCES);
        if (plannedSources.isEmpty()) {
            if (thesisId != null) {
                completeStep(planSteps, ResearchRunPlanService.STEP_PLAN_SOURCES,
                        "configuredSources=0, dynamicThesisSearch=enabled", 1);
                scheduleExecution(saved, plannedSources, planSteps);
                ResearchRunPlan plan = new ResearchRunPlan();
                plan.setRun(saved);
                plan.setPlannedSources(plannedSources);
                plan.setPlanSteps(planSteps);
                return plan;
            }
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
                .orElseThrow(() -> new ResourceNotFoundException("研究运行不存在：" + id));
    }

    public List<SourceProfile> plannedSources(Long id) {
        detail(id);
        return researchRunRepository.findSourcesByRunId(id);
    }

    public ResearchRunPlan resume(Long id) {
        ResearchRun run = detail(id);
        researchRuntimeService.resume(id);
        run.setStatus(ResearchEnums.RUN_STATUS_RUNNING);
        run.setErrorMessage(null);
        run.setSummary("Research runtime resumed from the latest checkpoint.");
        researchRunRepository.updateResult(run);
        List<SourceProfile> sources = plannedSources(id);
        List<ResearchRunPlanStep> steps = researchRunPlanService.findByRunId(id);
        scheduleExecution(run, sources, steps);
        ResearchRunPlan plan = new ResearchRunPlan();
        plan.setRun(run);
        plan.setPlannedSources(sources);
        plan.setPlanSteps(steps);
        return plan;
    }

    public ResearchReport regenerateReport(Long runId) {
        ResearchRun run = detail(runId);
        if (run.getThesisId() == null) {
            throw new BusinessConflictException("该研究运行未关联研究命题，无法重新生成研究报告");
        }
        com.finscope.domain.research.ResearchThesis thesis = researchThesisRepository.findById(run.getThesisId())
                .orElseThrow(() -> new ResourceNotFoundException("研究命题不存在：" + run.getThesisId()));
        try {
            ResearchRunContext.setCurrentRunId(runId);
            ResearchReport report = researchReportService.generate(runId);
            researchRunOutputService.deleteByType(runId, ResearchRunOutputService.BRIEF);
            run.setBriefDate(null);
            refreshOutputCounts(run);
            reconcileRegeneratedRun(run, report);
            run.setSummary("研究报告已补建：有效证据=" + value(report.getEvidenceCount())
                    + "，独立来源=" + value(report.getSourceCount())
                    + "，生成方式=" + report.getGenerationMode());
            run.setErrorMessage(null);
            researchRunRepository.updateResult(run);
            return report;
        } finally {
            ResearchRunContext.clear();
        }
    }

    private void reconcileRegeneratedRun(ResearchRun run, ResearchReport report) {
        Long runId = run.getId();
        completeSystemNode(run, ResearchRunPlanService.STEP_COMPOSE_REPORT, "SYNTHESIZE",
                "reportId=" + report.getId() + ", evidence=" + report.getEvidenceCount(), 1);
        completeSystemNode(run, "verify_output", "VERIFY",
                "补建报告校验完成：reportId=" + report.getId(), 1);
        researchRuntimeService.complete(runId);

        List<ResearchRunPlanStep> steps = researchRunPlanService.findByRunId(runId);
        recoverPlanStep(steps, ResearchRunPlanService.STEP_COMPOSE_REPORT,
                "reportId=" + report.getId() + ", evidence=" + report.getEvidenceCount()
                        + ", chars=" + report.getCharacterCount());
        recoverPlanStep(steps, ResearchRunPlanService.STEP_SUMMARIZE_RUN,
                "研究报告补建完成并通过输出校验");

        boolean partial = !"COMPLETED".equals(report.getStatus());
        run.setStatus(partial ? ResearchEnums.RUN_STATUS_PARTIAL_SUCCESS : ResearchEnums.RUN_STATUS_COMPLETED);
        researchMissionService.completeMission(runId, partial);
    }

    private void recoverPlanStep(List<ResearchRunPlanStep> steps, String stepId, String outputSummary) {
        if (steps == null) return;
        for (ResearchRunPlanStep step : steps) {
            if (!stepId.equals(step.getStepId()) || "COMPLETED".equals(step.getStatus())) continue;
            researchRunPlanService.start(step);
            researchRunPlanService.complete(step, outputSummary, 1);
            return;
        }
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
        safeRuntimeFailure(run.getId(), ResearchRunPlanService.STEP_FETCH_SOURCES,
                "RESEARCH_EXECUTOR_REJECTED", ex.getMessage());
        if (researchMissionService != null && run.getThesisId() != null) {
            researchMissionService.failMission(run.getId());
        }
    }

    private ResearchRun execute(ResearchRun run, List<SourceProfile> plannedSources, List<ResearchRunPlanStep> planSteps) {
        long start = System.currentTimeMillis();
        int articleBefore = articleRepository.countAll();
        int eventBefore = eventClusterRepository.countAll();
        int evidenceBefore = evidenceItemRepository.countAll();
        int learningBefore = learningTaskRepository.countAll();
        int ideaBefore = contentIdeaRepository.countAll();
        int fetchedSources = value(run.getFetchedSourceCount());
        int dynamicQueries = 0;
        List<String> errors = new ArrayList<String>();
        ResearchRunPlanStep currentStep = null;
        String activeMissionTask = null;
        try {
            ResearchRunContext.setCurrentRunId(run.getId());
            AgentRunContext context = ResearchRunContext.currentContext();
            ResearchThesis thesis = run.getThesisId() == null ? null
                    : researchThesisRepository.findById(run.getThesisId())
                    .orElseThrow(() -> new IllegalStateException("研究命题不存在：" + run.getThesisId()));
            List<ResearchMissionTask> missionTasks = new ArrayList<ResearchMissionTask>();
            ResearchMissionTask baselineTask = null;
            ResearchMissionTask synthesisTask = null;
            if (thesis != null) {
                missionTasks = researchMissionService.tasks(run.getId());
                if (missionTasks.isEmpty()) {
                    researchMissionService.plan(run, thesis);
                    missionTasks = researchMissionService.tasks(run.getId());
                }
                baselineTask = requiredMissionTask(missionTasks, "source_scan", "BASELINE");
                synthesisTask = requiredMissionTask(missionTasks, "report_synthesis", "SYNTHESIS");
                if (!researchMissionService.isFinished(run.getId(), baselineTask.getTaskKey())) {
                    researchMissionService.startTask(run.getId(), baselineTask.getTaskKey());
                    activeMissionTask = baselineTask.getTaskKey();
                }
            }
            completeSystemNode(run, ResearchRunPlanService.STEP_PLAN_SOURCES, "PLAN",
                    "plannedSources=" + plannedSources.size(), plannedSources.size());
            currentStep = startStep(planSteps, ResearchRunPlanService.STEP_FETCH_SOURCES);
            boolean runtimeStopped = false;
            int baselineEvidenceBefore = outputCount(run.getId(), ResearchRunOutputService.EVIDENCE);
            int baselineSourcesBefore = distinctArticleSources(run.getId());
            for (int sourceIndex = 0; sourceIndex < plannedSources.size(); sourceIndex++) {
                SourceProfile plannedSource = plannedSources.get(sourceIndex);
                Long sourceId = plannedSource.getSourceId();
                if (sourceId == null) {
                    continue;
                }
                String runtimeNode = "collect_source:" + sourceId + ":" + sourceIndex;
                RuntimeNodeStart runtimeStart = researchRuntimeService.startNode(run.getId(), runtimeNode, "COLLECT",
                        null, sourceFetchInput(sourceId, plannedSource));
                if (runtimeStart.isAlreadyCompleted()) {
                    continue;
                }
                if (!runtimeStart.isStarted()) {
                    errors.add("Runtime terminated: " + runtimeStart.getTerminationReason());
                    runtimeStopped = true;
                    break;
                }
                int progressBefore = researchProgress(run.getId());
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
                persistRunningProgress(run, fetchedSources);
                int progressAfter = researchProgress(run.getId());
                researchRuntimeService.completeNode(run.getId(), runtimeNode, runtimeStateHash(run.getId()),
                        Math.max(0, progressAfter - progressBefore), sourceFetchOutput(fetchRun));
            }

            if (thesis != null) {
                if (runtimeStopped) {
                    if (baselineTask.getTaskKey().equals(activeMissionTask)) {
                        researchMissionService.failTask(run.getId(), activeMissionTask,
                                "Runtime停止，基线扫描未完整完成");
                        activeMissionTask = null;
                    }
                } else if (baselineTask.getTaskKey().equals(activeMissionTask)) {
                    researchMissionService.completeTask(run.getId(), activeMissionTask,
                            "完成配置来源扫描，共处理" + fetchedSources + "个来源",
                            Math.max(0, outputCount(run.getId(), ResearchRunOutputService.EVIDENCE)
                                    - baselineEvidenceBefore),
                            Math.max(0, distinctArticleSources(run.getId()) - baselineSourcesBefore));
                    activeMissionTask = null;
                    researchMissionService.assess(run.getId(), baselineTask.getTaskKey());
                }
            }

            if (thesis != null && !runtimeStopped) {
                ResearchAgentLoopResult agentResult = researchAgentLoopService.run(run.getId(), run.getMode());
                dynamicQueries += agentResult.getExternalActionCount();
                if (!agentResult.isFinishAccepted()) {
                    errors.add("Research Agent已停止扩展检索，报告将声明证据局限："
                            + agentResult.getTerminationReason());
                }
            }

            completeStep(planSteps, ResearchRunPlanService.STEP_FETCH_SOURCES,
                    "fetchedSources=" + fetchedSources + ", dynamicQueries=" + dynamicQueries
                            + ", errors=" + errors.size(), fetchedSources + dynamicQueries);
            currentStep = startStep(planSteps, ResearchRunPlanService.STEP_CLASSIFY_EVENTS);
            completeSystemNode(run, ResearchRunPlanService.STEP_CLASSIFY_EVENTS, "ASSESS",
                    "events=" + outputCount(run.getId(), ResearchRunOutputService.EVENT),
                    outputCount(run.getId(), ResearchRunOutputService.EVENT));
            completeStep(planSteps, ResearchRunPlanService.STEP_CLASSIFY_EVENTS,
                    "events=" + outputCount(run.getId(), ResearchRunOutputService.EVENT),
                    outputCount(run.getId(), ResearchRunOutputService.EVENT));
            currentStep = startStep(planSteps, ResearchRunPlanService.STEP_EXTRACT_EVIDENCE);
            completeSystemNode(run, ResearchRunPlanService.STEP_EXTRACT_EVIDENCE, "ASSESS",
                    "evidence=" + outputCount(run.getId(), ResearchRunOutputService.EVIDENCE),
                    outputCount(run.getId(), ResearchRunOutputService.EVIDENCE));
            completeStep(planSteps, ResearchRunPlanService.STEP_EXTRACT_EVIDENCE,
                    "evidence=" + outputCount(run.getId(), ResearchRunOutputService.EVIDENCE),
                    outputCount(run.getId(), ResearchRunOutputService.EVIDENCE));
            currentStep = startStep(planSteps, ResearchRunPlanService.STEP_COMPOSE_REPORT);
            if (thesis != null && !researchMissionService.isFinished(run.getId(), synthesisTask.getTaskKey())) {
                activeMissionTask = synthesisTask.getTaskKey();
                researchMissionService.startTask(run.getId(), activeMissionTask);
            }
            RuntimeNodeStart reportStart = researchRuntimeService.startNode(run.getId(),
                    ResearchRunPlanService.STEP_COMPOSE_REPORT, "SYNTHESIZE", null, "runId=" + run.getId());
            ResearchReport report = reportStart.isAlreadyCompleted()
                    ? researchReportService.findByRunId(run.getId()).orElseThrow(
                    () -> new IllegalStateException("Runtime marked report complete but report is missing"))
                    : researchReportService.generate(run.getId());
            if (reportStart.isStarted()) {
                researchRuntimeService.completeNode(run.getId(), ResearchRunPlanService.STEP_COMPOSE_REPORT,
                        runtimeStateHash(run.getId()), 1,
                        "reportId=" + report.getId() + ", evidence=" + report.getEvidenceCount());
            }
            completeStep(planSteps, ResearchRunPlanService.STEP_COMPOSE_REPORT,
                    "reportId=" + report.getId() + ", evidence=" + report.getEvidenceCount()
                            + ", chars=" + report.getCharacterCount(), 1);
            if (synthesisTask != null && synthesisTask.getTaskKey().equals(activeMissionTask)) {
                researchMissionService.completeTask(run.getId(), activeMissionTask,
                        "报告已生成，reportId=" + report.getId(), 0, 0);
                activeMissionTask = null;
            }
            currentStep = startStep(planSteps, ResearchRunPlanService.STEP_SUMMARIZE_RUN);
            run.setFetchedSourceCount(fetchedSources);
            refreshOutputCounts(run);
            run.setLearningTaskCount(delta(learningBefore, learningTaskRepository.countAll()));
            run.setContentIdeaCount(delta(ideaBefore, contentIdeaRepository.countAll()));
            run.setBriefDate(null);
            run.setStatus(errors.isEmpty()
                    ? ResearchEnums.RUN_STATUS_COMPLETED
                    : ResearchEnums.RUN_STATUS_PARTIAL_SUCCESS);
            run.setSummary(resultSummary(run));
            run.setErrorMessage(errors.isEmpty() ? null : String.join("; ", errors));
            completeSystemNode(run, "verify_output", "VERIFY", run.getSummary(), 1);
            completeStep(planSteps, ResearchRunPlanService.STEP_SUMMARIZE_RUN, run.getSummary(), 1);
            agentRunRepository.record(run.getId(), null, null, "research-orchestrate", run.getStatus(),
                    "themes=" + String.join(",", run.getThemeCodes()) + ", date=" + run.getRunDate(),
                    run.getSummary(), run.getErrorMessage(), System.currentTimeMillis() - start);
            ResearchRun updated = researchRunRepository.updateResult(run);
            ResearchRuntimeCheckpoint completedRuntime = researchRuntimeService.complete(run.getId());
            if (thesis != null) {
                researchMissionService.completeMission(run.getId(), !errors.isEmpty(),
                        hardTerminationReason(completedRuntime));
            }
            return updated;
        } catch (Exception ex) {
            run.setFetchedSourceCount(fetchedSources);
            refreshOutputCounts(run);
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
            safeRuntimeFailure(run.getId(), currentStep == null ? "research-orchestrate" : currentStep.getStepId(),
                    "UNKNOWN", ex.getMessage());
            if (researchMissionService != null && run.getThesisId() != null) {
                if (activeMissionTask != null) {
                    researchMissionService.failTask(run.getId(), activeMissionTask, ex.getMessage());
                }
                researchMissionService.failMission(run.getId());
            }
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

    private int outputCount(Long runId, String outputType) {
        return researchRunOutputService == null ? 0 : researchRunOutputService.count(runId, outputType);
    }

    private void persistRunningProgress(ResearchRun run, int fetchedSources) {
        run.setFetchedSourceCount(fetchedSources);
        refreshOutputCounts(run);
        run.setSummary(resultSummary(run));
        researchRunRepository.updateResult(run);
    }

    private void refreshOutputCounts(ResearchRun run) {
        run.setArticleCount(outputCount(run.getId(), ResearchRunOutputService.ARTICLE));
        run.setEventCount(outputCount(run.getId(), ResearchRunOutputService.EVENT));
        run.setEvidenceCount(effectiveEvidenceCount(run.getId()));
    }

    private int researchProgress(Long runId) {
        return outputCount(runId, ResearchRunOutputService.ARTICLE)
                + outputCount(runId, ResearchRunOutputService.EVENT)
                + outputCount(runId, ResearchRunOutputService.EVIDENCE);
    }

    private String runtimeStateHash(Long runId) {
        return outputCount(runId, ResearchRunOutputService.ARTICLE) + ":"
                + outputCount(runId, ResearchRunOutputService.EVENT) + ":"
                + effectiveEvidenceCount(runId) + ":"
                + outputCount(runId, ResearchRunOutputService.REPORT) + ":"
                + (researchRunOutputService == null ? 0
                : researchRunOutputService.countDistinctArticleSources(runId));
    }

    private int effectiveEvidenceCount(Long runId) {
        int articleEvidence = outputCount(runId, ResearchRunOutputService.EVIDENCE);
        int searchEvidence = researchSearchEvidenceRepository == null
                ? 0 : researchSearchEvidenceRepository.countByRunId(runId);
        return articleEvidence + searchEvidence;
    }

    private int distinctArticleSources(Long runId) {
        return researchRunOutputService == null ? 0
                : researchRunOutputService.countDistinctArticleSources(runId);
    }

    private String hardTerminationReason(ResearchRuntimeCheckpoint checkpoint) {
        if (checkpoint == null || "COMPLETED".equals(checkpoint.getTerminationReason())) {
            return null;
        }
        return checkpoint.getTerminationReason();
    }

    private ResearchMissionTask requiredMissionTask(List<ResearchMissionTask> tasks,
                                                     String toolCode,
                                                     String intent) {
        ResearchMissionTask found = null;
        for (ResearchMissionTask task : tasks) {
            if (toolCode.equals(task.getToolCode()) && intent.equals(task.getIntent())) {
                if (found != null) {
                    throw new IllegalStateException("研究任务图包含重复系统阶段：" + toolCode + "/" + intent);
                }
                found = task;
            }
        }
        if (found == null) {
            throw new IllegalStateException("研究任务图缺少系统阶段：" + toolCode + "/" + intent);
        }
        return found;
    }

    private void completeSystemNode(ResearchRun run,
                                    String nodeId,
                                    String phase,
                                    String outputSummary,
                                    int progressDelta) {
        RuntimeNodeStart start = researchRuntimeService.startNode(run.getId(), nodeId, phase, null,
                "runId=" + run.getId());
        if (start.isStarted()) {
            researchRuntimeService.completeNode(run.getId(), nodeId, runtimeStateHash(run.getId()),
                    progressDelta, outputSummary);
        }
    }

    private void safeRuntimeFailure(Long runId, String nodeId, String errorType, String message) {
        try {
            if (researchRuntimeService.findCheckpoint(runId).isPresent()) {
                researchRuntimeService.failNode(runId, nodeId, errorType, message);
            }
        } catch (RuntimeException ignored) {
            // Preserve the original orchestration failure; runtime persistence is best-effort here.
        }
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
                + ", report=" + outputCount(run.getId(), ResearchRunOutputService.REPORT);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String sourceFetchInput(Long sourceId, SourceProfile plannedSource) {
        return "sourceId=" + sourceId + ", name=" + plannedSource.getSourceName();
    }

    private String sourceFetchOutput(FetchRun fetchRun) {
        if (fetchRun == null) {
            return "fetchResult=unavailable";
        }
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
        skipStep(planSteps, ResearchRunPlanService.STEP_COMPOSE_REPORT, "NO_PLANNED_SOURCES");
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
        safeRuntimeFailure(run.getId(), ResearchRunPlanService.STEP_PLAN_SOURCES,
                "NO_PLANNED_SOURCES", message);
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
