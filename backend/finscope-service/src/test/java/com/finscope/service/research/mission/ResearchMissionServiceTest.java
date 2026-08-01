package com.finscope.service.research.mission;

import com.finscope.dao.research.mission.ResearchMissionRepository;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchThesis;
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
}
