package com.finscope.service.research.mission;

import com.finscope.dao.research.mission.ResearchMissionRepository;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.mission.ResearchMissionGap;
import com.finscope.domain.research.mission.ResearchMethodBlueprint;
import com.finscope.domain.research.mission.ResearchMissionTask;
import com.finscope.service.research.report.EvidenceSufficiency;
import com.finscope.service.research.report.ResearchReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchMissionServiceTest {
    private ResearchMissionRepository repository;
    private ResearchPlanningAgent planningAgent;
    private ResearchReportService reportService;
    private ResearchMissionService service;

    @BeforeEach
    void setUp() {
        repository = mock(ResearchMissionRepository.class);
        planningAgent = mock(ResearchPlanningAgent.class);
        reportService = mock(ResearchReportService.class);
        service = new ResearchMissionService(repository, planningAgent, reportService,
                new ResearchEvidenceGapAnalyzer(), new ResearchToolRegistry());
    }

    @Test
    void initializesContractAndPersistsValidatedPlanAsDomainTasks() {
        ResearchRun run = run();
        ResearchThesis thesis = thesis();
        ResearchMissionDraft draft = new DeterministicResearchPlanner().plan(input());
        when(planningAgent.plan(any(ResearchPlanningInput.class)))
                .thenReturn(new ResearchPlanningResult(draft, "DETERMINISTIC", "PLAN_REJECTED",
                        "任务 search_counter 使用了未注册工具 external_browser"));

        service.initializePending(run, thesis, 12);
        ResearchPlanningResult result = service.plan(run, thesis);

        verify(repository).initialize(21L, "AI资本开支能否持续？", "AI算力",
                "等待研究规划", Arrays.asList("形成可验证的阶段性结论"), 12);
        ArgumentCaptor<List> tasks = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ResearchMethodBlueprint> blueprint = ArgumentCaptor.forClass(ResearchMethodBlueprint.class);
        verify(repository).replacePlan(eq(21L), eq("DETERMINISTIC"), anyString(), anyList(),
                blueprint.capture(), tasks.capture(), eq("PLAN_REJECTED"),
                eq("任务 search_counter 使用了未注册工具 external_browser"));
        assertEquals("GENERAL_RESEARCH", blueprint.getValue().getResearchType());
        assertEquals(6, tasks.getValue().size());
        assertEquals("baseline_scan", ((ResearchMissionTask) tasks.getValue().get(0)).getTaskKey());
        assertEquals("DETERMINISTIC", result.getPlanningMode());
    }

    @Test
    void persistsGapAndSkipsOnlyPendingSearchesWhenEvidenceIsSufficient() {
        when(reportService.assessSufficiency(21L))
                .thenReturn(EvidenceSufficiency.fromCounts(7, 3, 5, 2));
        when(repository.skipPendingTasksByTool(21L, "public_news_search", "SUFFICIENT_EVIDENCE"))
                .thenReturn(2);

        ResearchMissionGap gap = service.assess(21L, "search_primary");

        assertTrue(gap.isSufficient());
        verify(repository).appendGap(gap);
        verify(repository).skipPendingTasksByTool(21L, "public_news_search", "SUFFICIENT_EVIDENCE");
    }

    @Test
    void delegatesTaskLifecycleWithExplicitTerminalState() {
        when(repository.startTask(21L, "baseline_scan")).thenReturn(true);
        when(repository.completeTask(21L, "baseline_scan", "完成扫描", 4, 2)).thenReturn(true);
        when(repository.updateMissionStatus(21L, "PARTIAL_SUCCESS")).thenReturn(true);

        service.startTask(21L, "baseline_scan");
        service.completeTask(21L, "baseline_scan", "完成扫描", 4, 2);
        service.completeMission(21L, true);

        verify(repository).updateMissionStatus(21L, "PARTIAL_SUCCESS");
    }

    @Test
    void recordsSuccessfulAgentToolCallAgainstMatchingMissionTask() {
        ResearchMissionTask counter = missionTask("search_counter", "public_news_search", "COUNTER", "PENDING",
                "宁德时代  盈利质量\t风险");
        when(repository.findTask(21L, "search_counter")).thenReturn(java.util.Optional.of(counter));
        when(repository.startTask(21L, "search_counter")).thenReturn(true);
        when(repository.completeTask(21L, "search_counter", "找到独立反方证据", 2, 1)).thenReturn(true);
        ResearchAgentDecision decision = decision("public_news_search",
                "{\"query\":\"宁德时代 盈利质量 风险\",\"intent\":\"COUNTER\"}");
        ResearchToolObservation observation = observation("SUCCESS", "找到独立反方证据", 2, 1);

        String taskKey = service.recordAgentToolResult(21L, decision, observation);

        assertEquals("search_counter", taskKey);
        verify(repository).startTask(21L, "search_counter");
        verify(repository).completeTask(21L, "search_counter", "找到独立反方证据", 2, 1);
    }

    @Test
    void recordsEvidenceAssessmentAndFailedToolCallsWithTerminalTaskState() {
        ResearchMissionTask assess = missionTask("assess_evidence", "evidence_assess", "ASSESS", "PENDING", null);
        when(repository.findTask(21L, "assess_evidence")).thenReturn(java.util.Optional.of(assess));
        when(repository.startTask(21L, "assess_evidence")).thenReturn(true);
        when(repository.failTask(21L, "assess_evidence", "评估失败")).thenReturn(true);

        String taskKey = service.recordAgentToolResult(21L, decision("evidence_assess", "{}"),
                observation("TERMINAL_ERROR", "评估失败", 0, 0));

        assertEquals("assess_evidence", taskKey);
        verify(repository).failTask(21L, "assess_evidence", "评估失败");
    }

    @Test
    void recordsOnlyTheMissionTaskExplicitlySelectedByAgentDecision() {
        ResearchMissionTask first = missionTask("counter_accounting", "public_news_search", "COUNTER", "PENDING",
                "宁德时代 应收 存货 风险");
        ResearchMissionTask second = missionTask("counter_governance", "public_news_search", "COUNTER", "PENDING",
                "宁德时代 治理 资本配置 风险");
        when(repository.findTask(21L, "counter_governance")).thenReturn(java.util.Optional.of(second));
        when(repository.startTask(21L, "counter_governance")).thenReturn(true);
        when(repository.completeTask(21L, "counter_governance", "治理反证完成", 1, 1)).thenReturn(true);
        ResearchAgentDecision decision = decision("public_news_search",
                "{\"query\":\"宁德时代 治理 资本配置 风险\",\"intent\":\"COUNTER\"}");
        decision.setMissionTaskKey("counter_governance");

        String taskKey = service.recordAgentToolResult(21L, decision,
                observation("SUCCESS", "治理反证完成", 1, 1));

        assertEquals("counter_governance", taskKey);
        verify(repository).startTask(21L, "counter_governance");
        verify(repository, org.mockito.Mockito.never()).startTask(21L, first.getTaskKey());
    }

    @Test
    void sufficientAutomaticAssessmentCompletesAssessmentMissionTask() {
        ResearchMissionTask assess = missionTask("assess_evidence", "evidence_assess", "ASSESS", "PENDING", null);
        when(reportService.assessSufficiency(21L)).thenReturn(EvidenceSufficiency.fromCounts(8, 4, 5, 3));
        when(repository.findTasks(21L)).thenReturn(Arrays.asList(assess));
        when(repository.startTask(21L, "assess_evidence")).thenReturn(true);
        when(repository.completeTask(eq(21L), eq("assess_evidence"), anyString(), eq(0), eq(0))).thenReturn(true);

        ResearchMissionGap gap = service.assess(21L, "agent-decision-9");

        assertTrue(gap.isSufficient());
        verify(repository).completeTask(eq(21L), eq("assess_evidence"), anyString(), eq(0), eq(0));
    }

    @Test
    void terminalMissionSkipsUnfinishedTasksWithRuntimeReason() {
        when(repository.updateMissionStatus(21L, "PARTIAL_SUCCESS")).thenReturn(true);

        service.completeMission(21L, false, "NO_PROGRESS");

        verify(repository).skipUnfinishedTasks(21L, "RUNTIME_TERMINATED:NO_PROGRESS");
        verify(repository).updateMissionStatus(21L, "PARTIAL_SUCCESS");
    }

    @Test
    void failedMissionClosesEveryUnfinishedTask() {
        service.failMission(21L);

        verify(repository).skipUnfinishedTasks(21L, "MISSION_FAILED");
        verify(repository).updateMissionStatus(21L, "FAILED");
    }

    @Test
    void appliesOnlyBoundedAdaptivePatchAndRejectsImmutableTask() {
        when(repository.upsertAdaptiveTask(eq(21L), any(ResearchMissionTask.class))).thenReturn(true, false);
        ResearchPlanPatch patch = new ResearchPlanPatch();
        patch.setOperation("ADD_OR_REPLACE_PENDING_TASK");
        patch.setTaskKey("adaptive_counter_2");
        patch.setTitle("寻找需求下修的一手材料");
        patch.setQuestion("是否存在订单或指引下修？");
        patch.setToolCode("public_news_search");
        patch.setIntent("COUNTER");
        patch.setQueryText("AI算力 指引 下调 订单 风险");
        patch.setReason("原查询没有新增独立来源");

        ResearchMissionTask saved = service.applyPatch(21L, patch);

        assertEquals("adaptive_counter_2", saved.getTaskKey());
        assertEquals("COUNTER", saved.getIntent());
        assertThrows(IllegalStateException.class, () -> service.applyPatch(21L, patch));
    }

    private ResearchRun run() {
        ResearchRun run = new ResearchRun();
        run.setId(21L);
        run.setRunDate(LocalDate.of(2026, 7, 26));
        run.setThemeCodes(Arrays.asList("ai_compute"));
        return run;
    }

    private ResearchThesis thesis() {
        ResearchThesis thesis = new ResearchThesis();
        thesis.setQuestion("AI资本开支能否持续？");
        thesis.setSubjectName("AI算力");
        thesis.setSubjectType("THEME");
        return thesis;
    }

    private ResearchPlanningInput input() {
        ResearchPlanningInput input = new ResearchPlanningInput();
        input.setQuestion("AI资本开支能否持续？");
        input.setSubjectName("AI算力");
        input.setSubjectType("THEME");
        input.setThemeCodes(Arrays.asList("ai_compute"));
        input.setMaxActions(12);
        input.setCurrentDate(LocalDate.of(2026, 7, 26));
        return input;
    }

    private ResearchMissionTask missionTask(String key, String tool, String intent, String status, String query) {
        ResearchMissionTask value = new ResearchMissionTask();
        value.setTaskKey(key);
        value.setToolCode(tool);
        value.setIntent(intent);
        value.setStatus(status);
        value.setQueryText(query);
        return value;
    }

    private ResearchAgentDecision decision(String tool, String argumentsJson) {
        ResearchAgentDecision value = new ResearchAgentDecision();
        value.setToolCode(tool);
        value.setArgumentsJson(argumentsJson);
        value.setMissionTaskKey("evidence_assess".equals(tool) ? "assess_evidence" : "search_counter");
        return value;
    }

    private ResearchToolObservation observation(String status, String summary, int evidenceDelta, int sourceDelta) {
        ResearchToolObservation value = new ResearchToolObservation();
        value.setStatus(status);
        value.setObservationSummary(summary);
        value.setEvidenceDelta(evidenceDelta);
        value.setSourceDelta(sourceDelta);
        return value;
    }
}
