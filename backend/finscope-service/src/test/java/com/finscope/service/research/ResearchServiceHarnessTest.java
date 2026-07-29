package com.finscope.service.research;

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
import com.finscope.domain.agent.AgentNodeResult;
import com.finscope.domain.fetch.FetchRun;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchRunPlan;
import com.finscope.domain.research.ResearchRunPlanStep;
import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.SourceProfile;
import com.finscope.domain.research.ThemeProfile;
import com.finscope.domain.research.mission.ResearchMissionGap;
import com.finscope.domain.research.mission.ResearchMissionTask;
import com.finscope.domain.source.Source;
import com.finscope.service.agent.ActionFingerprintService;
import com.finscope.service.agent.AgentHarness;
import com.finscope.service.agent.AgentTraceService;
import com.finscope.service.fetch.FetchService;
import com.finscope.service.research.report.ResearchReportService;
import com.finscope.service.research.report.EvidenceSufficiency;
import com.finscope.service.research.mission.ResearchMissionService;
import com.finscope.service.research.agent.ResearchAgentLoopResult;
import com.finscope.service.research.agent.ResearchAgentLoopService;
import com.finscope.service.research.runtime.ResearchRuntimeService;
import com.finscope.service.research.runtime.RuntimeNodeStart;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchServiceHarnessTest {
    @Test
    void createsRunPlanBeforeBackgroundExecutionFetchesSources() {
        ResearchService service = new ResearchService();
        ThemeProfileService themeProfileService = mock(ThemeProfileService.class);
        SourcePlanner sourcePlanner = mock(SourcePlanner.class);
        SourceRepository sourceRepository = mock(SourceRepository.class);
        ResearchRunRepository researchRunRepository = mock(ResearchRunRepository.class);
        FetchService fetchService = mock(FetchService.class);
        ResearchReportService reportService = mock(ResearchReportService.class);
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        EventClusterRepository eventClusterRepository = mock(EventClusterRepository.class);
        EvidenceItemRepository evidenceItemRepository = mock(EvidenceItemRepository.class);
        LearningTaskRepository learningTaskRepository = mock(LearningTaskRepository.class);
        ContentIdeaRepository contentIdeaRepository = mock(ContentIdeaRepository.class);
        AgentTraceService agentTraceService = mock(AgentTraceService.class);
        ResearchRunPlanService researchRunPlanService = mock(ResearchRunPlanService.class);
        ResearchRunOutputService researchRunOutputService = mock(ResearchRunOutputService.class);
        ResearchRuntimeService researchRuntimeService = runtimeService();
        CapturingExecutor researchTaskExecutor = new CapturingExecutor();

        ReflectionTestUtils.setField(service, "themeProfileService", themeProfileService);
        ReflectionTestUtils.setField(service, "sourcePlanner", sourcePlanner);
        ReflectionTestUtils.setField(service, "sourceRepository", sourceRepository);
        ReflectionTestUtils.setField(service, "researchRunRepository", researchRunRepository);
        ReflectionTestUtils.setField(service, "fetchService", fetchService);
        ReflectionTestUtils.setField(service, "researchReportService", reportService);
        ReflectionTestUtils.setField(service, "articleRepository", articleRepository);
        ReflectionTestUtils.setField(service, "eventClusterRepository", eventClusterRepository);
        ReflectionTestUtils.setField(service, "evidenceItemRepository", evidenceItemRepository);
        ReflectionTestUtils.setField(service, "learningTaskRepository", learningTaskRepository);
        ReflectionTestUtils.setField(service, "contentIdeaRepository", contentIdeaRepository);
        ReflectionTestUtils.setField(service, "agentRunRepository", mock(AgentRunRepository.class));
        ReflectionTestUtils.setField(service, "agentHarness", new AgentHarness());
        ReflectionTestUtils.setField(service, "actionFingerprintService", new ActionFingerprintService());
        ReflectionTestUtils.setField(service, "agentTraceService", agentTraceService);
        ReflectionTestUtils.setField(service, "researchRunPlanService", researchRunPlanService);
        ReflectionTestUtils.setField(service, "researchRunOutputService", researchRunOutputService);
        ReflectionTestUtils.setField(service, "researchRuntimeService", researchRuntimeService);
        ReflectionTestUtils.setField(service, "researchTaskExecutor", researchTaskExecutor);

        LocalDate runDate = LocalDate.of(2026, 7, 9);
        List<ResearchRunPlanStep> defaultSteps = defaultSteps();
        when(themeProfileService.getRequired(anyList())).thenReturn(Collections.singletonList(theme()));
        when(sourceRepository.findAll()).thenReturn(Collections.emptyList());
        when(sourcePlanner.plan(any(LocalDate.class), anyList(), anyList()))
                .thenReturn(Collections.singletonList(source(12L)));
        when(researchRunPlanService.initializeDefaultPlan(501L, 1)).thenReturn(defaultSteps);
        when(researchRunPlanService.findStep(anyList(), any())).thenAnswer(invocation -> {
            List<ResearchRunPlanStep> steps = invocation.getArgument(0);
            String stepId = invocation.getArgument(1);
            for (ResearchRunPlanStep step : steps) {
                if (stepId.equals(step.getStepId())) {
                    return step;
                }
            }
            return steps.get(0);
        });
        when(researchRunPlanService.start(any(ResearchRunPlanStep.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(researchRunPlanService.complete(any(ResearchRunPlanStep.class), any(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(researchRunPlanService.fail(any(ResearchRunPlanStep.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(researchRunRepository.save(any(ResearchRun.class))).thenAnswer(invocation -> {
            ResearchRun run = invocation.getArgument(0);
            run.setId(501L);
            return run;
        });
        when(researchRunRepository.updateResult(any(ResearchRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fetchService.fetch(12L)).thenReturn(fetchRun());
        when(reportService.generate(501L)).thenReturn(report(501L));

        ResearchRunPlan plan = service.createRun(runDate,
                Collections.singletonList(ResearchEnums.THEME_MARKET));

        assertEquals(ResearchEnums.RUN_STATUS_RUNNING, plan.getRun().getStatus());
        assertEquals(6, plan.getPlanSteps().size());
        verify(fetchService, never()).fetch(anyLong());
        verify(researchRuntimeService).initialize(501L, ResearchRuntimeService.DEFAULT_MAX_ACTIONS);

        researchTaskExecutor.runCaptured();

        verify(fetchService).fetch(12L);
        verify(researchRuntimeService).startNode(eq(501L), eq("collect_source:12:0"), eq("COLLECT"),
                isNull(), anyString());
        verify(researchRunRepository, atLeastOnce()).updateResult(any(ResearchRun.class));
    }

    @Test
    void skipsRepeatedSourceFetchAtHardThresholdAndRecordsTrace() {
        ResearchService service = new ResearchService();
        ThemeProfileService themeProfileService = mock(ThemeProfileService.class);
        SourcePlanner sourcePlanner = mock(SourcePlanner.class);
        SourceRepository sourceRepository = mock(SourceRepository.class);
        ResearchRunRepository researchRunRepository = mock(ResearchRunRepository.class);
        FetchService fetchService = mock(FetchService.class);
        ResearchReportService reportService = mock(ResearchReportService.class);
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        EventClusterRepository eventClusterRepository = mock(EventClusterRepository.class);
        EvidenceItemRepository evidenceItemRepository = mock(EvidenceItemRepository.class);
        LearningTaskRepository learningTaskRepository = mock(LearningTaskRepository.class);
        ContentIdeaRepository contentIdeaRepository = mock(ContentIdeaRepository.class);
        AgentTraceService agentTraceService = mock(AgentTraceService.class);
        ResearchRunPlanService researchRunPlanService = mock(ResearchRunPlanService.class);
        ResearchRunOutputService researchRunOutputService = mock(ResearchRunOutputService.class);
        CapturingExecutor researchTaskExecutor = new CapturingExecutor();

        ReflectionTestUtils.setField(service, "themeProfileService", themeProfileService);
        ReflectionTestUtils.setField(service, "sourcePlanner", sourcePlanner);
        ReflectionTestUtils.setField(service, "sourceRepository", sourceRepository);
        ReflectionTestUtils.setField(service, "researchRunRepository", researchRunRepository);
        ReflectionTestUtils.setField(service, "fetchService", fetchService);
        ReflectionTestUtils.setField(service, "researchReportService", reportService);
        ReflectionTestUtils.setField(service, "articleRepository", articleRepository);
        ReflectionTestUtils.setField(service, "eventClusterRepository", eventClusterRepository);
        ReflectionTestUtils.setField(service, "evidenceItemRepository", evidenceItemRepository);
        ReflectionTestUtils.setField(service, "learningTaskRepository", learningTaskRepository);
        ReflectionTestUtils.setField(service, "contentIdeaRepository", contentIdeaRepository);
        ReflectionTestUtils.setField(service, "agentRunRepository", mock(AgentRunRepository.class));
        ReflectionTestUtils.setField(service, "agentHarness", new AgentHarness());
        ReflectionTestUtils.setField(service, "actionFingerprintService", new ActionFingerprintService());
        ReflectionTestUtils.setField(service, "agentTraceService", agentTraceService);
        ReflectionTestUtils.setField(service, "researchRunPlanService", researchRunPlanService);
        ReflectionTestUtils.setField(service, "researchRunOutputService", researchRunOutputService);
        ReflectionTestUtils.setField(service, "researchRuntimeService", runtimeService());
        ReflectionTestUtils.setField(service, "researchTaskExecutor", researchTaskExecutor);

        LocalDate runDate = LocalDate.of(2026, 7, 3);
        List<ResearchRunPlanStep> defaultSteps = defaultSteps();
        when(themeProfileService.getRequired(anyList())).thenReturn(Collections.singletonList(theme()));
        when(sourceRepository.findAll()).thenReturn(Collections.emptyList());
        when(sourcePlanner.plan(any(LocalDate.class), anyList(), anyList()))
                .thenReturn(Arrays.asList(source(12L), source(12L), source(12L)));
        when(researchRunPlanService.initializeDefaultPlan(501L, 3)).thenReturn(defaultSteps);
        when(researchRunPlanService.findStep(anyList(), any())).thenAnswer(invocation -> {
            List<ResearchRunPlanStep> steps = invocation.getArgument(0);
            String stepId = invocation.getArgument(1);
            for (ResearchRunPlanStep step : steps) {
                if (stepId.equals(step.getStepId())) {
                    return step;
                }
            }
            return steps.get(0);
        });
        when(researchRunPlanService.start(any(ResearchRunPlanStep.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(researchRunPlanService.complete(any(ResearchRunPlanStep.class), any(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(researchRunPlanService.fail(any(ResearchRunPlanStep.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(researchRunRepository.save(any(ResearchRun.class))).thenAnswer(invocation -> {
            ResearchRun run = invocation.getArgument(0);
            run.setId(501L);
            return run;
        });
        when(researchRunRepository.updateResult(any(ResearchRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fetchService.fetch(12L)).thenReturn(fetchRun());
        when(reportService.generate(501L)).thenReturn(report(501L));

        ResearchRunPlan plan = service.createRun(runDate,
                Collections.singletonList(ResearchEnums.THEME_MARKET));
        researchTaskExecutor.runCaptured();

        assertEquals(6, plan.getPlanSteps().size());
        verify(researchRunPlanService).initializeDefaultPlan(501L, 3);
        verify(researchRunPlanService, atLeastOnce()).complete(any(ResearchRunPlanStep.class), any(), anyInt());
        verify(fetchService, times(2)).fetch(12L);
        ArgumentCaptor<AgentNodeResult> resultCaptor = ArgumentCaptor.forClass(AgentNodeResult.class);
        verify(agentTraceService, times(3)).recordNode(isNull(), isNull(), any(), any(),
                resultCaptor.capture(), anyLong(), any());
        AgentNodeResult skipped = resultCaptor.getAllValues().get(2);
        assertEquals("SKIPPED", skipped.getStatus());
        assertEquals("REPEATED_ACTION", skipped.getErrorType());
        assertEquals(ResearchEnums.RUN_STATUS_PARTIAL_SUCCESS, plan.getRun().getStatus());
        assertTrue(plan.getRun().getErrorMessage().contains("Repeated action reached hard threshold"));
    }

    @Test
    void persistsRunningProgressAfterEachFetchedSource() {
        ResearchService service = new ResearchService();
        ThemeProfileService themeProfileService = mock(ThemeProfileService.class);
        SourcePlanner sourcePlanner = mock(SourcePlanner.class);
        SourceRepository sourceRepository = mock(SourceRepository.class);
        ResearchRunRepository researchRunRepository = mock(ResearchRunRepository.class);
        FetchService fetchService = mock(FetchService.class);
        ResearchReportService reportService = mock(ResearchReportService.class);
        ResearchRunPlanService researchRunPlanService = mock(ResearchRunPlanService.class);
        ResearchRunOutputService outputService = mock(ResearchRunOutputService.class);
        CapturingExecutor executor = new CapturingExecutor();
        AtomicInteger completedFetches = new AtomicInteger();
        List<String> snapshots = new ArrayList<String>();

        ReflectionTestUtils.setField(service, "themeProfileService", themeProfileService);
        ReflectionTestUtils.setField(service, "sourcePlanner", sourcePlanner);
        ReflectionTestUtils.setField(service, "sourceRepository", sourceRepository);
        ReflectionTestUtils.setField(service, "researchRunRepository", researchRunRepository);
        ReflectionTestUtils.setField(service, "researchThesisRepository", mock(ResearchThesisRepository.class));
        ReflectionTestUtils.setField(service, "fetchService", fetchService);
        ReflectionTestUtils.setField(service, "researchReportService", reportService);
        ReflectionTestUtils.setField(service, "articleRepository", mock(ArticleRepository.class));
        ReflectionTestUtils.setField(service, "eventClusterRepository", mock(EventClusterRepository.class));
        ReflectionTestUtils.setField(service, "evidenceItemRepository", mock(EvidenceItemRepository.class));
        ReflectionTestUtils.setField(service, "learningTaskRepository", mock(LearningTaskRepository.class));
        ReflectionTestUtils.setField(service, "contentIdeaRepository", mock(ContentIdeaRepository.class));
        ReflectionTestUtils.setField(service, "agentRunRepository", mock(AgentRunRepository.class));
        ReflectionTestUtils.setField(service, "agentHarness", new AgentHarness());
        ReflectionTestUtils.setField(service, "actionFingerprintService", new ActionFingerprintService());
        ReflectionTestUtils.setField(service, "agentTraceService", mock(AgentTraceService.class));
        ReflectionTestUtils.setField(service, "researchRunPlanService", researchRunPlanService);
        ReflectionTestUtils.setField(service, "researchRunOutputService", outputService);
        ReflectionTestUtils.setField(service, "researchRuntimeService", runtimeService());
        ReflectionTestUtils.setField(service, "researchTaskExecutor", executor);

        LocalDate runDate = LocalDate.of(2026, 7, 13);
        List<ResearchRunPlanStep> steps = defaultSteps();
        when(themeProfileService.getRequired(anyList())).thenReturn(Collections.singletonList(theme()));
        when(sourceRepository.findAll()).thenReturn(Collections.emptyList());
        when(sourcePlanner.plan(any(LocalDate.class), anyList(), anyList()))
                .thenReturn(Arrays.asList(source(21L), source(22L)));
        when(researchRunPlanService.initializeDefaultPlan(501L, 2)).thenReturn(steps);
        when(researchRunPlanService.findStep(anyList(), any())).thenAnswer(invocation -> {
            String stepId = invocation.getArgument(1);
            for (ResearchRunPlanStep step : steps) {
                if (stepId.equals(step.getStepId())) {
                    return step;
                }
            }
            return steps.get(0);
        });
        when(researchRunPlanService.start(any(ResearchRunPlanStep.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(researchRunPlanService.complete(any(ResearchRunPlanStep.class), any(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(researchRunRepository.save(any(ResearchRun.class))).thenAnswer(invocation -> {
            ResearchRun run = invocation.getArgument(0);
            run.setId(501L);
            return run;
        });
        when(researchRunRepository.updateResult(any(ResearchRun.class))).thenAnswer(invocation -> {
            ResearchRun run = invocation.getArgument(0);
            snapshots.add(run.getStatus() + "|" + run.getFetchedSourceCount() + "|" + run.getArticleCount()
                    + "|" + run.getEventCount() + "|" + run.getEvidenceCount());
            return run;
        });
        when(fetchService.fetch(anyLong())).thenAnswer(invocation -> {
            completedFetches.incrementAndGet();
            FetchRun run = fetchRun();
            run.setSourceId(invocation.getArgument(0));
            run.setSourceName("Source " + invocation.getArgument(0));
            return run;
        });
        when(outputService.count(501L, ResearchRunOutputService.ARTICLE))
                .thenAnswer(invocation -> completedFetches.get() * 2);
        when(outputService.count(501L, ResearchRunOutputService.EVENT))
                .thenAnswer(invocation -> completedFetches.get());
        when(outputService.count(501L, ResearchRunOutputService.EVIDENCE))
                .thenAnswer(invocation -> completedFetches.get() * 2);
        when(reportService.generate(501L)).thenReturn(report(501L));

        service.createRun(runDate, Collections.singletonList(ResearchEnums.THEME_MARKET));
        executor.runCaptured();

        assertTrue(snapshots.contains("RUNNING|1|2|1|2"), "progress snapshots=" + snapshots);
        assertTrue(snapshots.contains("RUNNING|2|4|2|4"), "progress snapshots=" + snapshots);
        assertTrue(snapshots.contains("COMPLETED|2|4|2|4"), "progress snapshots=" + snapshots);
    }

    @Test
    void failsRunWithVisiblePlanWhenNoSourcesArePlanned() {
        ResearchService service = new ResearchService();
        ThemeProfileService themeProfileService = mock(ThemeProfileService.class);
        SourcePlanner sourcePlanner = mock(SourcePlanner.class);
        SourceRepository sourceRepository = mock(SourceRepository.class);
        ResearchRunRepository researchRunRepository = mock(ResearchRunRepository.class);
        FetchService fetchService = mock(FetchService.class);
        ResearchReportService reportService = mock(ResearchReportService.class);
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        EventClusterRepository eventClusterRepository = mock(EventClusterRepository.class);
        EvidenceItemRepository evidenceItemRepository = mock(EvidenceItemRepository.class);
        LearningTaskRepository learningTaskRepository = mock(LearningTaskRepository.class);
        ContentIdeaRepository contentIdeaRepository = mock(ContentIdeaRepository.class);
        AgentTraceService agentTraceService = mock(AgentTraceService.class);
        ResearchRunPlanService researchRunPlanService = mock(ResearchRunPlanService.class);

        ReflectionTestUtils.setField(service, "themeProfileService", themeProfileService);
        ReflectionTestUtils.setField(service, "sourcePlanner", sourcePlanner);
        ReflectionTestUtils.setField(service, "sourceRepository", sourceRepository);
        ReflectionTestUtils.setField(service, "researchRunRepository", researchRunRepository);
        ReflectionTestUtils.setField(service, "fetchService", fetchService);
        ReflectionTestUtils.setField(service, "researchReportService", reportService);
        ReflectionTestUtils.setField(service, "articleRepository", articleRepository);
        ReflectionTestUtils.setField(service, "eventClusterRepository", eventClusterRepository);
        ReflectionTestUtils.setField(service, "evidenceItemRepository", evidenceItemRepository);
        ReflectionTestUtils.setField(service, "learningTaskRepository", learningTaskRepository);
        ReflectionTestUtils.setField(service, "contentIdeaRepository", contentIdeaRepository);
        ReflectionTestUtils.setField(service, "agentRunRepository", mock(AgentRunRepository.class));
        ReflectionTestUtils.setField(service, "agentHarness", new AgentHarness());
        ReflectionTestUtils.setField(service, "actionFingerprintService", new ActionFingerprintService());
        ReflectionTestUtils.setField(service, "agentTraceService", agentTraceService);
        ReflectionTestUtils.setField(service, "researchRunPlanService", researchRunPlanService);
        ReflectionTestUtils.setField(service, "researchRuntimeService", runtimeService());

        LocalDate runDate = LocalDate.of(2026, 7, 9);
        List<ResearchRunPlanStep> defaultSteps = defaultSteps();
        when(themeProfileService.getRequired(anyList())).thenReturn(Collections.singletonList(theme()));
        when(sourceRepository.findAll()).thenReturn(Collections.emptyList());
        when(sourcePlanner.plan(any(LocalDate.class), anyList(), anyList()))
                .thenReturn(Collections.emptyList());
        when(researchRunPlanService.initializeDefaultPlan(501L, 0)).thenReturn(defaultSteps);
        when(researchRunPlanService.findStep(anyList(), any())).thenAnswer(invocation -> {
            List<ResearchRunPlanStep> steps = invocation.getArgument(0);
            String stepId = invocation.getArgument(1);
            for (ResearchRunPlanStep step : steps) {
                if (stepId.equals(step.getStepId())) {
                    return step;
                }
            }
            return steps.get(0);
        });
        when(researchRunPlanService.start(any(ResearchRunPlanStep.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(researchRunPlanService.complete(any(ResearchRunPlanStep.class), any(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(researchRunPlanService.fail(any(ResearchRunPlanStep.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(researchRunPlanService.skip(any(ResearchRunPlanStep.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(researchRunRepository.save(any(ResearchRun.class))).thenAnswer(invocation -> {
            ResearchRun run = invocation.getArgument(0);
            run.setId(501L);
            return run;
        });
        when(researchRunRepository.updateResult(any(ResearchRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResearchRunPlan plan = service.createRun(runDate,
                Collections.singletonList(ResearchEnums.THEME_MARKET));

        assertEquals(6, plan.getPlanSteps().size());
        assertEquals(ResearchEnums.RUN_STATUS_FAILED, plan.getRun().getStatus());
        assertTrue(plan.getRun().getErrorMessage().contains("No planned sources"));
        verify(reportService, never()).generate(anyLong());
        verify(researchRunPlanService).fail(any(ResearchRunPlanStep.class), any(), any());
        verify(researchRunPlanService, atLeastOnce()).skip(any(ResearchRunPlanStep.class), any());
    }

    @Test
    void regeneratingLegacyRunDetachesItsBriefOutputAfterReportIsPersisted() {
        ResearchService service = new ResearchService();
        ResearchRunRepository runRepository = mock(ResearchRunRepository.class);
        ResearchThesisRepository thesisRepository = mock(ResearchThesisRepository.class);
        ResearchReportService reportService = mock(ResearchReportService.class);
        ResearchRunOutputService outputService = mock(ResearchRunOutputService.class);
        ResearchSearchEvidenceRepository searchEvidenceRepository = mock(ResearchSearchEvidenceRepository.class);
        FetchService fetchService = mock(FetchService.class);
        ResearchRunPlanService planService = mock(ResearchRunPlanService.class);
        ResearchRuntimeService runtimeService = runtimeService();
        ResearchMissionService missionService = mock(ResearchMissionService.class);
        EvidenceSufficiency sufficiency = mock(EvidenceSufficiency.class);
        ResearchRun run = new ResearchRun();
        run.setId(15L);
        run.setThesisId(1L);
        run.setStatus(ResearchEnums.RUN_STATUS_FAILED);
        run.setErrorMessage("研究运行没有可引用的有效证据");
        run.setBriefDate(LocalDate.of(2026, 7, 13));
        ResearchThesis thesis = new ResearchThesis();
        thesis.setId(1L);
        ResearchReport regenerated = report(15L);
        regenerated.setStatus("COMPLETED_WITH_GAPS");
        List<ResearchRunPlanStep> steps = defaultSteps();

        ReflectionTestUtils.setField(service, "researchRunRepository", runRepository);
        ReflectionTestUtils.setField(service, "researchThesisRepository", thesisRepository);
        ReflectionTestUtils.setField(service, "researchReportService", reportService);
        ReflectionTestUtils.setField(service, "researchRunOutputService", outputService);
        ReflectionTestUtils.setField(service, "researchSearchEvidenceRepository", searchEvidenceRepository);
        ReflectionTestUtils.setField(service, "fetchService", fetchService);
        ReflectionTestUtils.setField(service, "researchRunPlanService", planService);
        ReflectionTestUtils.setField(service, "researchRuntimeService", runtimeService);
        ReflectionTestUtils.setField(service, "researchMissionService", missionService);
        when(runRepository.findById(15L)).thenReturn(java.util.Optional.of(run));
        when(thesisRepository.findById(1L)).thenReturn(java.util.Optional.of(thesis));
        when(reportService.assessSufficiency(15L)).thenReturn(sufficiency);
        when(sufficiency.isSufficient()).thenReturn(false);
        when(reportService.generate(15L)).thenReturn(regenerated);
        when(searchEvidenceRepository.countByRunId(15L)).thenReturn(8);
        when(planService.findByRunId(15L)).thenReturn(steps);
        when(planService.findStep(anyList(), anyString())).thenAnswer(invocation -> {
            String stepId = invocation.getArgument(1);
            return steps.stream().filter(step -> stepId.equals(step.getStepId())).findFirst().get();
        });
        when(planService.start(any(ResearchRunPlanStep.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planService.complete(any(ResearchRunPlanStep.class), anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(runRepository.updateResult(any(ResearchRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.regenerateReport(15L);

        verify(fetchService, never()).fetch(any(Source.class));
        verify(outputService).deleteByType(15L, ResearchRunOutputService.BRIEF);
        verify(runtimeService).startNode(eq(15L), eq(ResearchRunPlanService.STEP_COMPOSE_REPORT),
                eq("SYNTHESIZE"), isNull(), anyString());
        verify(runtimeService).startNode(eq(15L), eq("verify_output"), eq("VERIFY"), isNull(), anyString());
        verify(runtimeService).complete(15L);
        verify(missionService).completeMission(15L, true);
        assertEquals(null, run.getBriefDate());
        assertEquals(ResearchEnums.RUN_STATUS_PARTIAL_SUCCESS, run.getStatus());
        assertEquals(8, run.getEvidenceCount());
        assertEquals(null, run.getErrorMessage());
        assertTrue(run.getSummary().contains("研究报告已补建"));
        assertTrue(!run.getSummary().contains("briefDate"));
    }

    @Test
    void drivesThesisResearchFromPersistedMissionTasks() {
        ResearchService service = new ResearchService();
        ThemeProfileService themes = mock(ThemeProfileService.class);
        SourcePlanner planner = mock(SourcePlanner.class);
        SourceRepository sources = mock(SourceRepository.class);
        ResearchRunRepository runs = mock(ResearchRunRepository.class);
        ResearchThesisRepository theses = mock(ResearchThesisRepository.class);
        FetchService fetches = mock(FetchService.class);
        ResearchReportService reports = mock(ResearchReportService.class);
        ResearchMissionService missions = mock(ResearchMissionService.class);
        ResearchAgentLoopService agentLoop = mock(ResearchAgentLoopService.class);
        ResearchRunPlanService plans = mock(ResearchRunPlanService.class);
        ResearchRunOutputService outputs = mock(ResearchRunOutputService.class);
        CapturingExecutor executor = new CapturingExecutor();
        ResearchThesis thesis = new ResearchThesis();
        thesis.setId(3L);
        thesis.setQuestion("AI资本开支能否持续？");
        thesis.setSubjectName("AI算力");
        thesis.setSubjectType("THEME");
        List<ResearchMissionTask> missionTasks = Arrays.asList(
                missionTask("scan_context", "基线扫描", "source_scan", "BASELINE"),
                missionTask("search_support", "支持证据搜索", "public_news_search", "SUPPORT"),
                missionTask("search_counter", "反方证据搜索", "public_news_search", "COUNTER"),
                missionTask("judge_evidence", "证据判断", "evidence_assess", "ASSESS"),
                missionTask("write_report", "报告合成", "report_synthesis", "SYNTHESIS"));

        ReflectionTestUtils.setField(service, "themeProfileService", themes);
        ReflectionTestUtils.setField(service, "sourcePlanner", planner);
        ReflectionTestUtils.setField(service, "sourceRepository", sources);
        ReflectionTestUtils.setField(service, "researchRunRepository", runs);
        ReflectionTestUtils.setField(service, "researchThesisRepository", theses);
        ReflectionTestUtils.setField(service, "fetchService", fetches);
        ReflectionTestUtils.setField(service, "researchReportService", reports);
        ReflectionTestUtils.setField(service, "researchMissionService", missions);
        ReflectionTestUtils.setField(service, "researchAgentLoopService", agentLoop);
        ReflectionTestUtils.setField(service, "articleRepository", mock(ArticleRepository.class));
        ReflectionTestUtils.setField(service, "eventClusterRepository", mock(EventClusterRepository.class));
        ReflectionTestUtils.setField(service, "evidenceItemRepository", mock(EvidenceItemRepository.class));
        ReflectionTestUtils.setField(service, "learningTaskRepository", mock(LearningTaskRepository.class));
        ReflectionTestUtils.setField(service, "contentIdeaRepository", mock(ContentIdeaRepository.class));
        ReflectionTestUtils.setField(service, "agentRunRepository", mock(AgentRunRepository.class));
        ReflectionTestUtils.setField(service, "agentHarness", new AgentHarness());
        ReflectionTestUtils.setField(service, "actionFingerprintService", new ActionFingerprintService());
        ReflectionTestUtils.setField(service, "agentTraceService", mock(AgentTraceService.class));
        ReflectionTestUtils.setField(service, "researchRunPlanService", plans);
        ReflectionTestUtils.setField(service, "researchRunOutputService", outputs);
        ReflectionTestUtils.setField(service, "researchRuntimeService", runtimeService());
        ReflectionTestUtils.setField(service, "researchTaskExecutor", executor);

        List<ResearchRunPlanStep> steps = defaultSteps();
        when(theses.findById(3L)).thenReturn(java.util.Optional.of(thesis));
        when(themes.getRequired(anyList())).thenReturn(Collections.singletonList(theme()));
        when(sources.findAll()).thenReturn(Collections.emptyList());
        when(planner.plan(any(LocalDate.class), anyList(), anyList()))
                .thenReturn(Collections.singletonList(source(12L)));
        when(runs.save(any(ResearchRun.class))).thenAnswer(invocation -> {
            ResearchRun run = invocation.getArgument(0);
            run.setId(501L);
            return run;
        });
        when(runs.updateResult(any(ResearchRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(plans.initializeDefaultPlan(501L, 1)).thenReturn(steps);
        when(plans.findStep(anyList(), any())).thenAnswer(invocation -> {
            String stepId = invocation.getArgument(1);
            for (ResearchRunPlanStep step : steps) if (stepId.equals(step.getStepId())) return step;
            return steps.get(0);
        });
        when(plans.start(any(ResearchRunPlanStep.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(plans.complete(any(ResearchRunPlanStep.class), any(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(missions.tasks(501L)).thenReturn(Collections.<ResearchMissionTask>emptyList(), missionTasks);
        when(missions.assess(eq(501L), anyString())).thenAnswer(invocation -> {
            ResearchMissionGap gap = new ResearchMissionGap();
            gap.setSufficient(false);
            return gap;
        });
        when(fetches.fetch(12L)).thenReturn(fetchRun());
        when(fetches.fetch(any(Source.class))).thenReturn(fetchRun());
        when(reports.generate(501L)).thenReturn(report(501L));
        when(agentLoop.run(501L, com.finscope.domain.research.ResearchMode.DEEP))
                .thenReturn(ResearchAgentLoopResult.aborted(9, 3,
                "EVIDENCE_INSUFFICIENT"));

        service.createRun(3L, LocalDate.of(2026, 7, 26),
                Collections.singletonList(ResearchEnums.THEME_MARKET), com.finscope.domain.research.ResearchMode.DEEP);
        executor.runCaptured();

        verify(missions).initializePending(any(ResearchRun.class), eq(thesis),
                eq(ResearchRuntimeService.DEFAULT_MAX_ACTIONS));
        verify(missions).plan(any(ResearchRun.class), eq(thesis));
        verify(missions).startTask(501L, "scan_context");
        verify(missions).assess(501L, "scan_context");
        verify(agentLoop).run(501L, com.finscope.domain.research.ResearchMode.DEEP);
        verify(missions).startTask(501L, "write_report");
        verify(reports).generate(501L);
        verify(missions).completeMission(501L, true, null);
    }

    private ThemeProfile theme() {
        ThemeProfile theme = new ThemeProfile();
        theme.setCode(ResearchEnums.THEME_MARKET);
        theme.setName("市场");
        return theme;
    }

    private SourceProfile source(Long sourceId) {
        SourceProfile source = new SourceProfile();
        source.setSourceId(sourceId);
        source.setSourceName("Source " + sourceId);
        source.setSourceTier(ResearchEnums.SOURCE_TIER_MEDIA);
        source.setThemeCodes(Collections.singletonList(ResearchEnums.THEME_MARKET));
        source.setCredibility(4);
        source.setEnabled(true);
        return source;
    }

    private FetchRun fetchRun() {
        FetchRun run = new FetchRun();
        run.setSourceId(12L);
        run.setSourceName("Source 12");
        run.setStatus("SUCCESS");
        run.setSuccessCount(2);
        run.setDuplicateCount(1);
        return run;
    }

    private ResearchReport report(Long runId) {
        ResearchReport report = new ResearchReport();
        report.setId(91L);
        report.setResearchRunId(runId);
        report.setEvidenceCount(4);
        report.setSourceCount(3);
        report.setCharacterCount(4000);
        report.setGenerationMode("DETERMINISTIC");
        return report;
    }

    private ResearchMissionTask missionTask(String key, String title, String toolCode, String intent) {
        ResearchMissionTask task = new ResearchMissionTask();
        task.setTaskKey(key);
        task.setTitle(title);
        task.setToolCode(toolCode);
        task.setIntent(intent);
        task.setStatus("PENDING");
        task.setQueryText(title + " 最新事实");
        return task;
    }

    private List<ResearchRunPlanStep> defaultSteps() {
        return Arrays.asList(
                step(ResearchRunPlanService.STEP_PLAN_SOURCES),
                step(ResearchRunPlanService.STEP_FETCH_SOURCES),
                step(ResearchRunPlanService.STEP_CLASSIFY_EVENTS),
                step(ResearchRunPlanService.STEP_EXTRACT_EVIDENCE),
                step(ResearchRunPlanService.STEP_COMPOSE_REPORT),
                step(ResearchRunPlanService.STEP_SUMMARIZE_RUN));
    }

    private ResearchRunPlanStep step(String stepId) {
        ResearchRunPlanStep step = new ResearchRunPlanStep();
        step.setResearchRunId(501L);
        step.setStepId(stepId);
        step.setTitle(stepId);
        step.setStatus("PENDING");
        return step;
    }

    private static class CapturingExecutor implements Executor {
        private Runnable captured;

        @Override
        public void execute(Runnable command) {
            this.captured = command;
        }

        void runCaptured() {
            captured.run();
        }
    }

    private ResearchRuntimeService runtimeService() {
        ResearchRuntimeService runtime = mock(ResearchRuntimeService.class);
        ResearchRuntimeCheckpoint checkpoint = new ResearchRuntimeCheckpoint();
        checkpoint.setResearchRunId(501L);
        checkpoint.setStatus("RUNNING");
        checkpoint.setMaxActions(12);
        when(runtime.startNode(anyLong(), anyString(), anyString(), any(), anyString()))
                .thenReturn(RuntimeNodeStart.started(checkpoint));
        when(runtime.completeNode(anyLong(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(checkpoint);
        when(runtime.complete(anyLong())).thenReturn(checkpoint);
        return runtime;
    }
}
