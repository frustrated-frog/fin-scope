package com.finscope.service.research;

import com.finscope.dao.research.ResearchRunPlanRepository;
import com.finscope.domain.research.ResearchRunPlanStep;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResearchRunPlanServiceTest {
    @Test
    void initializesDefaultPlanAndCompletesSteps() {
        ResearchRunPlanRepository repository = mock(ResearchRunPlanRepository.class);
        when(repository.replaceForRun(eq(501L), anyList())).thenAnswer(invocation -> invocation.getArgument(1));
        when(repository.update(any(ResearchRunPlanStep.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResearchRunPlanService service = new ResearchRunPlanService(repository);

        List<ResearchRunPlanStep> steps = service.initializeDefaultPlan(501L, 3);

        assertEquals(Arrays.asList(ResearchRunPlanService.STEP_PLAN_SOURCES,
                        ResearchRunPlanService.STEP_FETCH_SOURCES,
                        ResearchRunPlanService.STEP_CLASSIFY_EVENTS,
                        ResearchRunPlanService.STEP_EXTRACT_EVIDENCE,
                        ResearchRunPlanService.STEP_COMPOSE_REPORT,
                        ResearchRunPlanService.STEP_SUMMARIZE_RUN),
                steps.stream().map(ResearchRunPlanStep::getStepId).collect(Collectors.toList()));
        assertEquals("PENDING", steps.get(0).getStatus());
        assertEquals(1, steps.get(0).getMaxAttempts());

        ResearchRunPlanStep completed = service.complete(steps.get(0), "planned 3 sources", 3);

        assertEquals("COMPLETED", completed.getStatus());
        assertEquals("planned 3 sources", completed.getOutputSummary());
        assertEquals(3, completed.getProgressDelta());
    }

    @Test
    void failsStepWithClassifiedError() {
        ResearchRunPlanRepository repository = mock(ResearchRunPlanRepository.class);
        when(repository.update(any(ResearchRunPlanStep.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ResearchRunPlanService service = new ResearchRunPlanService(repository);
        ResearchRunPlanStep step = new ResearchRunPlanStep();
        step.setResearchRunId(501L);
        step.setStepId(ResearchRunPlanService.STEP_COMPOSE_REPORT);
        step.setTitle("生成研究报告");
        step.setStatus("RUNNING");

        ResearchRunPlanStep failed = service.fail(step, "BRIEF_GENERATION_FAILED", "brief render failed");

        assertEquals("FAILED", failed.getStatus());
        assertEquals("BRIEF_GENERATION_FAILED", failed.getErrorType());
        assertEquals("brief render failed", failed.getErrorMessage());
    }
}
