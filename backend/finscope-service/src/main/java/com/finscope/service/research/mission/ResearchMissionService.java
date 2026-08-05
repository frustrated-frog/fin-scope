package com.finscope.service.research.mission;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.research.mission.ResearchMissionRepository;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.mission.ResearchMission;
import com.finscope.domain.research.mission.ResearchMissionGap;
import com.finscope.domain.research.mission.ResearchMethodBlueprint;
import com.finscope.domain.research.mission.ResearchMissionTask;
import com.finscope.domain.research.mission.ResearchMissionView;
import com.finscope.domain.research.mission.ResearchToolDescriptor;
import com.finscope.service.research.report.EvidenceSufficiency;
import com.finscope.service.research.report.ResearchReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ResearchMissionService {
    private final ResearchMissionRepository repository;
    private final ResearchPlanningAgent planningAgent;
    private final ResearchReportService reportService;
    private final ResearchEvidenceGapAnalyzer gapAnalyzer;
    private final ResearchToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResearchMissionService(ResearchMissionRepository repository,
                                  ResearchPlanningAgent planningAgent,
                                  ResearchReportService reportService,
                                  ResearchEvidenceGapAnalyzer gapAnalyzer,
                                  ResearchToolRegistry toolRegistry) {
        this.repository = repository;
        this.planningAgent = planningAgent;
        this.reportService = reportService;
        this.gapAnalyzer = gapAnalyzer;
        this.toolRegistry = toolRegistry;
    }

    public ResearchMission initializePending(ResearchRun run, ResearchThesis thesis, int maxActions) {
        if (run == null || run.getId() == null) {
            throw new BusinessException(BizErrorCode.RESEARCH_RUN_REQUIRED);
        }
        String goal = thesis == null || blank(thesis.getQuestion()) ? "形成可验证的阶段性研究结论"
                : thesis.getQuestion().trim();
        String subject = thesis == null || blank(thesis.getSubjectName()) ? "研究对象"
                : thesis.getSubjectName().trim();
        if (thesis != null && !blank(thesis.getSubjectCode())
                && thesis.getSubjectCode().trim().matches("\\d{6}")) {
            subject += "（" + thesis.getSubjectCode().trim() + "）";
        }
        return repository.initialize(run.getId(), goal, subject, "等待研究规划",
                Arrays.asList("形成可验证的阶段性结论"), maxActions);
    }

    public ResearchPlanningResult plan(ResearchRun run, ResearchThesis thesis) {
        if (run == null || run.getId() == null || thesis == null) {
            throw new BusinessException(BizErrorCode.RESEARCH_RUN_AND_THESIS_REQUIRED);
        }
        int maxActions = repository.findMission(run.getId())
                .map(ResearchMission::getMaxActions).orElse(12);
        ResearchPlanningInput input = new ResearchPlanningInput();
        input.setQuestion(thesis.getQuestion());
        input.setSubjectType(thesis.getSubjectType());
        input.setSubjectName(thesis.getSubjectName());
        input.setSubjectCode(thesis.getSubjectCode());
        input.setThemeCodes(run.getThemeCodes());
        input.setMaxActions(maxActions);
        input.setCurrentDate(run.getRunDate() == null ? LocalDate.now() : run.getRunDate());
        ResearchPlanningResult result = planningAgent.plan(input);

        List<ResearchMissionTask> tasks = new ArrayList<ResearchMissionTask>();
        for (ResearchMissionTaskDraft task : result.getDraft().getTasks()) {
            tasks.add(task.toDomain());
        }
        ResearchMethodBlueprint blueprint = new ResearchMethodBlueprint();
        blueprint.setResearchType(result.getDraft().getResearchType());
        blueprint.setMethodCodes(result.getDraft().getMethodCodes());
        blueprint.setRequiredEvidence(result.getDraft().getRequiredEvidence());
        blueprint.setRequiredCalculations(result.getDraft().getRequiredCalculations());
        blueprint.setCounterChecks(result.getDraft().getCounterChecks());
        blueprint.setCompletionCriteria(result.getDraft().getCompletionCriteria());
        repository.replacePlan(run.getId(), result.getPlanningMode(), result.getDraft().getScopeSummary(),
                result.getDraft().getSuccessCriteria(), blueprint, tasks, result.getFallbackReason(),
                result.getRejectionDetail());
        return result;
    }

    public void startTask(Long runId, String taskKey) {
        if (!repository.startTask(runId, taskKey)) {
            throw new IllegalStateException("研究任务无法开始：" + runId + "/" + taskKey);
        }
    }

    public void completeTask(Long runId,
                             String taskKey,
                             String outputSummary,
                             int evidenceDelta,
                             int sourceDelta) {
        if (!repository.completeTask(runId, taskKey, outputSummary, evidenceDelta, sourceDelta)) {
            throw new IllegalStateException("研究任务无法完成：" + runId + "/" + taskKey);
        }
    }

    public void failTask(Long runId, String taskKey, String message) {
        repository.failTask(runId, taskKey, compact(message, 300));
    }

    @Transactional
    public String recordAgentToolResult(Long runId,
                                        ResearchAgentDecision decision,
                                        ResearchToolObservation observation) {
        ResearchMissionTask task = matchingTask(runId, decision);
        if (task == null || !repository.startTask(runId, task.getTaskKey())) {
            return null;
        }
        String summary = compact(observation == null ? null : observation.getObservationSummary(), 300);
        if (isFailure(observation)) {
            repository.failTask(runId, task.getTaskKey(), blank(summary) ? "Agent 工具执行失败" : summary);
        } else {
            int evidenceDelta = observation == null ? 0 : observation.getEvidenceDelta();
            int sourceDelta = observation == null ? 0 : observation.getSourceDelta();
            repository.completeTask(runId, task.getTaskKey(),
                    blank(summary) ? "Agent 工具执行完成" : summary, evidenceDelta, sourceDelta);
        }
        return task.getTaskKey();
    }

    public ResearchMissionGap assess(Long runId, String afterTaskKey) {
        EvidenceSufficiency sufficiency = reportService.assessSufficiency(runId);
        ResearchMissionGap gap = gapAnalyzer.assess(runId, afterTaskKey, sufficiency);
        repository.appendGap(gap);
        if (gap.isSufficient()) {
            repository.skipPendingTasksByTool(runId, "public_news_search", "SUFFICIENT_EVIDENCE");
            repository.skipPendingTasksByTool(runId, "research_material_search", "SUFFICIENT_EVIDENCE");
            completeSufficientAssessment(runId, gap);
        }
        return gap;
    }

    public boolean isFinished(Long runId, String taskKey) {
        return repository.findTask(runId, taskKey)
                .map(task -> "COMPLETED".equals(task.getStatus()) || "SKIPPED".equals(task.getStatus()))
                .orElse(false);
    }

    public List<ResearchMissionTask> tasks(Long runId) {
        return repository.findTasks(runId);
    }

    public ResearchMissionTask applyPatch(Long runId, ResearchPlanPatch patch) {
        if (runId == null || patch == null
                || !"ADD_OR_REPLACE_PENDING_TASK".equals(patch.getOperation())) {
            throw new IllegalArgumentException("只允许增加或替换未完成的局部研究任务");
        }
        if (blank(patch.getTaskKey()) || !patch.getTaskKey().matches("adaptive_[a-z0-9_]{1,48}")) {
            throw new IllegalArgumentException("局部任务编码必须使用 adaptive_ 前缀");
        }
        if (!"public_news_search".equals(patch.getToolCode())) {
            throw new IllegalArgumentException("局部重规划只能使用公开新闻搜索工具");
        }
        Set<String> intents = new HashSet<String>(Arrays.asList("SUPPORT", "COUNTER", "PRIMARY", "UPDATE"));
        if (!intents.contains(patch.getIntent())) {
            throw new IllegalArgumentException("局部重规划意图不在白名单中");
        }
        if (blank(patch.getTitle()) || blank(patch.getQuestion()) || blank(patch.getQueryText())
                || patch.getQueryText().length() > 180 || patch.getQueryText().contains("://")) {
            throw new IllegalArgumentException("局部重规划任务字段未通过安全校验");
        }
        ResearchMissionTask task = new ResearchMissionTask();
        task.setResearchRunId(runId);
        task.setTaskKey(patch.getTaskKey());
        task.setTitle(compact(patch.getTitle(), 100));
        task.setQuestion(compact(patch.getQuestion(), 240));
        task.setTaskType("SEARCH");
        task.setToolCode(patch.getToolCode());
        task.setIntent(patch.getIntent());
        task.setDependencies(new ArrayList<String>());
        task.setParallelGroup("adaptive_evidence");
        task.setQueryText(patch.getQueryText().trim());
        task.setRationale(compact(patch.getReason(), 240));
        task.setExpectedEvidence("能够改变当前 Evidence Gap 的独立公开来源");
        if (!repository.upsertAdaptiveTask(runId, task)) {
            throw new IllegalStateException("局部任务已经执行或正在执行，不能被重写：" + patch.getTaskKey());
        }
        return task;
    }

    public void completeMission(Long runId, boolean partial) {
        completeMission(runId, partial, null);
    }

    public void completeMission(Long runId, boolean partial, String terminationReason) {
        String status = partial || !blank(terminationReason) ? "PARTIAL_SUCCESS" : "COMPLETED";
        String skipReason = blank(terminationReason)
                ? "MISSION_FINALIZED"
                : "RUNTIME_TERMINATED:" + compact(terminationReason, 120);
        repository.skipUnfinishedTasks(runId, skipReason);
        if (!repository.updateMissionStatus(runId, status)) {
            throw new IllegalStateException("研究任务图无法进入终态：" + runId);
        }
    }

    public void failMission(Long runId) {
        repository.skipUnfinishedTasks(runId, "MISSION_FAILED");
        repository.updateMissionStatus(runId, "FAILED");
    }

    public ResearchMissionView detail(Long runId) {
        return findDetail(runId)
                .orElseThrow(() -> new ResourceNotFoundException("研究任务图不存在：" + runId));
    }

    public Optional<ResearchMissionView> findDetail(Long runId) {
        Optional<ResearchMission> found = repository.findMission(runId);
        if (!found.isPresent()) {
            return Optional.empty();
        }
        ResearchMission mission = found.get();
        List<ResearchMissionTask> tasks = repository.findTasks(runId);
        Set<String> usedTools = new HashSet<String>();
        for (ResearchMissionTask task : tasks) {
            usedTools.add(task.getToolCode());
        }
        List<ResearchToolDescriptor> tools = new ArrayList<ResearchToolDescriptor>();
        for (ResearchToolDescriptor tool : toolRegistry.list()) {
            if (usedTools.contains(tool.getCode())) {
                tools.add(tool);
            }
        }
        ResearchMissionView view = new ResearchMissionView();
        view.setMission(mission);
        view.setTasks(tasks);
        view.setGaps(repository.findGaps(runId));
        view.setTools(tools);
        return Optional.of(view);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ResearchMissionTask matchingTask(Long runId, ResearchAgentDecision decision) {
        if (decision == null || blank(decision.getToolCode()) || blank(decision.getMissionTaskKey())) return null;
        Optional<ResearchMissionTask> found = repository.findTask(runId, decision.getMissionTaskKey().trim());
        if (!found.isPresent()) return null;
        ResearchMissionTask task = found.get();
        if (!eligible(task) || !decision.getToolCode().equals(task.getToolCode())) return null;
        Map<String, Object> arguments = parseArguments(decision.getArgumentsJson());
        if ("public_news_search".equals(decision.getToolCode())) {
            String query = text(arguments.get("query"));
            if (!task.getIntent().equals(upper(arguments.get("intent")))
                    || query == null || !ResearchQueryNormalizer.normalize(task.getQueryText())
                    .equals(ResearchQueryNormalizer.normalize(query))) return null;
        }
        if ("research_material_search".equals(decision.getToolCode())
                && !matchesMaterial(task, text(arguments.get("stockCode")), upper(arguments.get("materialType")),
                text(arguments.get("query")))) {
            return null;
        }
        return task;
    }

    private void completeSufficientAssessment(Long runId, ResearchMissionGap gap) {
        for (ResearchMissionTask task : repository.findTasks(runId)) {
            if (eligible(task) && "evidence_assess".equals(task.getToolCode())
                    && "ASSESS".equals(task.getIntent()) && repository.startTask(runId, task.getTaskKey())) {
                repository.completeTask(runId, task.getTaskKey(),
                        "自动证据评估已达到门槛：证据=" + gap.getEvidenceCount()
                                + "，独立来源=" + gap.getSourceCount() + "，支持=" + gap.getSupportCount()
                                + "，反方=" + gap.getCounterCount(), 0, 0);
                return;
            }
        }
    }

    private Map<String, Object> parseArguments(String json) {
        if (blank(json)) return java.util.Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception ignored) {
            return java.util.Collections.emptyMap();
        }
    }

    private boolean eligible(ResearchMissionTask task) {
        return task != null && ("PENDING".equals(task.getStatus())
                || "FAILED".equals(task.getStatus()) || "INTERRUPTED".equals(task.getStatus()));
    }

    private boolean matchesMaterial(ResearchMissionTask task, String stockCode, String materialType, String query) {
        if (blank(stockCode) || blank(materialType) || blank(task.getQueryText())) return false;
        String[] expected = task.getQueryText().trim().split("\\s+", 3);
        String expectedQuery = expected.length == 3 ? ResearchQueryNormalizer.normalize(expected[2]) : "";
        return expected.length >= 2 && expected[0].equals(stockCode.trim())
                && expected[1].equals(materialType.trim())
                && expectedQuery.equals(ResearchQueryNormalizer.normalize(query));
    }

    private boolean isFailure(ResearchToolObservation observation) {
        return observation == null || "TERMINAL_ERROR".equals(observation.getStatus())
                || "RETRYABLE_ERROR".equals(observation.getStatus())
                || "FAILED".equals(observation.getStatus());
    }

    private String upper(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String compact(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String compacted = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compacted.length() <= maxLength ? compacted : compacted.substring(0, maxLength);
    }
}
