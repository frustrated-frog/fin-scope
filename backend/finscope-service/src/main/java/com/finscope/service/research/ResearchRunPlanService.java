package com.finscope.service.research;

import com.finscope.dao.research.ResearchRunPlanRepository;
import com.finscope.domain.research.ResearchRunPlanStep;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ResearchRunPlanService {
    public static final String STEP_PLAN_SOURCES = "plan_sources";
    public static final String STEP_FETCH_SOURCES = "fetch_sources";
    public static final String STEP_CLASSIFY_EVENTS = "classify_events";
    public static final String STEP_EXTRACT_EVIDENCE = "extract_evidence";
    public static final String STEP_COMPOSE_REPORT = "compose_report";
    public static final String STEP_SUMMARIZE_RUN = "summarize_run";

    private final ResearchRunPlanRepository repository;

    public ResearchRunPlanService(ResearchRunPlanRepository repository) {
        this.repository = repository;
    }

    public List<ResearchRunPlanStep> initializeDefaultPlan(Long researchRunId, int sourceCount) {
        List<ResearchRunPlanStep> steps = new ArrayList<ResearchRunPlanStep>();
        steps.add(step(researchRunId, STEP_PLAN_SOURCES, "规划来源", "PLANNING", "SourcePlanner",
                Collections.<String>emptyList(), "sourceCount=" + sourceCount));
        steps.add(step(researchRunId, STEP_FETCH_SOURCES, "抓取来源", "FETCH", "FetchService",
                Collections.singletonList(STEP_PLAN_SOURCES), "sourceCount=" + sourceCount));
        steps.add(step(researchRunId, STEP_CLASSIFY_EVENTS, "归并事件", "EVENT", "EventClusterService",
                Collections.singletonList(STEP_FETCH_SOURCES), ""));
        steps.add(step(researchRunId, STEP_EXTRACT_EVIDENCE, "抽取证据", "EVIDENCE", "EvidenceService",
                Collections.singletonList(STEP_CLASSIFY_EVENTS), ""));
        steps.add(step(researchRunId, STEP_COMPOSE_REPORT, "生成研究报告", "REPORT", "ResearchReportService",
                Collections.singletonList(STEP_EXTRACT_EVIDENCE), ""));
        steps.add(step(researchRunId, STEP_SUMMARIZE_RUN, "汇总运行", "SUMMARY", "ResearchService",
                Collections.singletonList(STEP_COMPOSE_REPORT), ""));
        return repository.replaceForRun(researchRunId, steps);
    }

    public List<ResearchRunPlanStep> findByRunId(Long researchRunId) {
        return repository.findByRunId(researchRunId);
    }

    public ResearchRunPlanStep findStep(List<ResearchRunPlanStep> steps, String stepId) {
        if (steps == null) {
            throw new IllegalArgumentException("Research run plan steps are missing");
        }
        for (ResearchRunPlanStep step : steps) {
            if (stepId.equals(step.getStepId())) {
                return step;
            }
        }
        throw new IllegalArgumentException("Research run plan step not found: " + stepId);
    }

    public ResearchRunPlanStep start(ResearchRunPlanStep step) {
        step.setStatus("RUNNING");
        step.setAttempt(step.getAttempt() + 1);
        step.setStartedAt(LocalDateTime.now());
        step.setEndedAt(null);
        step.setErrorType(null);
        step.setErrorMessage(null);
        return repository.update(step);
    }

    public ResearchRunPlanStep complete(ResearchRunPlanStep step, String outputSummary, int progressDelta) {
        step.setStatus("COMPLETED");
        step.setOutputSummary(outputSummary);
        step.setProgressDelta(progressDelta);
        step.setEndedAt(LocalDateTime.now());
        step.setErrorType(null);
        step.setErrorMessage(null);
        step.setTerminationReason(null);
        return repository.update(step);
    }

    public ResearchRunPlanStep fail(ResearchRunPlanStep step, String errorType, String errorMessage) {
        step.setStatus("FAILED");
        step.setErrorType(errorType);
        step.setErrorMessage(errorMessage);
        step.setTerminationReason(errorType);
        step.setEndedAt(LocalDateTime.now());
        return repository.update(step);
    }

    public ResearchRunPlanStep skip(ResearchRunPlanStep step, String reason) {
        step.setStatus("SKIPPED");
        step.setTerminationReason(reason);
        step.setEndedAt(LocalDateTime.now());
        return repository.update(step);
    }

    private ResearchRunPlanStep step(Long researchRunId,
                                     String stepId,
                                     String title,
                                     String stepType,
                                     String executor,
                                     List<String> dependencies,
                                     String inputSummary) {
        ResearchRunPlanStep step = new ResearchRunPlanStep();
        step.setResearchRunId(researchRunId);
        step.setStepId(stepId);
        step.setTitle(title);
        step.setStepType(stepType);
        step.setExecutor(executor);
        step.setStatus("PENDING");
        step.setDependencies(dependencies);
        step.setInputSummary(inputSummary);
        step.setAttempt(0);
        step.setMaxAttempts(1);
        step.setProgressDelta(0);
        return step;
    }
}
