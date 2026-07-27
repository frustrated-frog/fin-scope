package com.finscope.service.research.evaluation;

import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.evaluation.ResearchEvaluation;
import com.finscope.domain.research.agent.ResearchAgentTrajectoryMetrics;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.domain.research.runtime.ResearchRuntimeEvent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchEvaluationScorerTest {
    private final ResearchEvaluationScorer scorer = new ResearchEvaluationScorer();

    @Test
    void passesACompleteEvidenceBackedRunWithValidTrace() {
        ResearchRun run = run(ResearchEnums.RUN_STATUS_COMPLETED);
        ResearchReport report = report(4, 3);
        ResearchRuntimeCheckpoint checkpoint = checkpoint("COMPLETED", 0, 12, 0);

        ResearchEvaluation evaluation = scorer.score(new ResearchEvaluationSnapshot(run, report, checkpoint,
                validCompletedEvents(), 4, 3));

        assertEquals(100, evaluation.getScore());
        assertEquals("PASS", evaluation.getGateStatus());
        assertTrue(evaluation.getCriticalIssues().isEmpty());
        assertEquals(6, evaluation.getMetrics().size());
    }

    @Test
    void blocksCompletedRunWithoutReportAsCriticalIntegrityFailure() {
        ResearchEvaluation evaluation = scorer.score(new ResearchEvaluationSnapshot(
                run(ResearchEnums.RUN_STATUS_COMPLETED), null, checkpoint("COMPLETED", 2, 12, 0),
                Arrays.asList(event(1, "RUN_CREATED", null), event(2, "TERMINATED", "complete")), 0, 0));

        assertEquals("BLOCK", evaluation.getGateStatus());
        assertTrue(evaluation.getCriticalIssues().contains("COMPLETED_WITHOUT_REPORT"));
    }

    @Test
    void blocksBudgetOverrunAndInvalidEventSequence() {
        ResearchEvaluation evaluation = scorer.score(new ResearchEvaluationSnapshot(
                run(ResearchEnums.RUN_STATUS_PARTIAL_SUCCESS), report(2, 1), checkpoint("TERMINATED", 13, 12, 1),
                Arrays.asList(event(2, "RUN_CREATED", null), event(2, "NODE_COMPLETED", "compose_report")), 2, 1));

        assertEquals("BLOCK", evaluation.getGateStatus());
        assertTrue(evaluation.getCriticalIssues().contains("BUDGET_OVERRUN"));
        assertTrue(evaluation.getCriticalIssues().contains("INVALID_EVENT_SEQUENCE"));
    }

    @Test
    void blocksCheckpointConflictAndDanglingStartedNode() {
        ResearchRuntimeCheckpoint checkpoint = checkpoint("INTERRUPTED", 2, 12, 0);
        ResearchRuntimeEvent started = event(2, "NODE_STARTED", "compose_report");
        ResearchEvaluation evaluation = scorer.score(new ResearchEvaluationSnapshot(
                run(ResearchEnums.RUN_STATUS_COMPLETED), report(3, 2), checkpoint,
                Arrays.asList(event(1, "RUN_CREATED", null), started), 3, 2));

        assertEquals("BLOCK", evaluation.getGateStatus());
        assertTrue(evaluation.getCriticalIssues().contains("CHECKPOINT_RUN_STATUS_CONFLICT"));
        assertTrue(evaluation.getCriticalIssues().contains("DANGLING_STARTED_NODE"));
    }

    @Test
    void blocksSelfReportedEvidenceAndCompletionWithoutMatchingStart() {
        ResearchRuntimeCheckpoint checkpoint = checkpoint("COMPLETED", 0, 12, 0);
        ResearchEvaluation evaluation = scorer.score(new ResearchEvaluationSnapshot(
                run(ResearchEnums.RUN_STATUS_COMPLETED), report(4, 3), checkpoint,
                Arrays.asList(event(1, "RUN_CREATED", null),
                        event(2, "NODE_COMPLETED", "compose_report"), event(3, "TERMINATED", "complete")),
                1, 1));

        assertEquals("BLOCK", evaluation.getGateStatus());
        assertTrue(evaluation.getCriticalIssues().contains("REPORT_OUTPUT_MISMATCH"));
        assertTrue(evaluation.getCriticalIssues().contains("INVALID_EVENT_SEQUENCE"));
    }

    @Test
    void appendsAgentTrajectoryAsSupplementaryMetricWithoutReweightingReportScore() {
        ResearchEvaluation evaluation = scorer.score(new ResearchEvaluationSnapshot(run(ResearchEnums.RUN_STATUS_COMPLETED),
                report(4, 3), checkpoint("COMPLETED", 0, 12, 0), validCompletedEvents(), 4, 3));
        ResearchAgentTrajectoryMetrics trajectory = new ResearchAgentTrajectoryMetrics();
        trajectory.setQualityScore(82);
        trajectory.setDecisionCount(6);
        trajectory.setDuplicateActionRate(0.1D);
        trajectory.setNoProgressRate(0.2D);

        scorer.appendTrajectoryMetric(evaluation, trajectory);

        assertEquals(100, evaluation.getScore());
        assertEquals(7, evaluation.getMetrics().size());
        assertEquals("agent_trajectory", evaluation.getMetrics().get(6).getMetricCode());
        assertEquals(82, evaluation.getMetrics().get(6).getScore());
    }

    private List<ResearchRuntimeEvent> validCompletedEvents() {
        java.util.ArrayList<ResearchRuntimeEvent> events = new java.util.ArrayList<ResearchRuntimeEvent>();
        events.add(event(1, "RUN_CREATED", null));
        String[] nodes = {"plan_sources", "classify_events", "extract_evidence", "compose_report", "verify_output"};
        int sequence = 2;
        for (String node : nodes) {
            events.add(event(sequence++, "NODE_STARTED", node));
            events.add(event(sequence++, "NODE_COMPLETED", node));
        }
        events.add(event(sequence, "TERMINATED", "complete"));
        return events;
    }

    private ResearchRun run(String status) {
        ResearchRun run = new ResearchRun();
        run.setId(7L);
        run.setStatus(status);
        run.setFetchedSourceCount(3);
        run.setEvidenceCount(4);
        return run;
    }

    private ResearchReport report(int evidence, int sources) {
        ResearchReport report = new ResearchReport();
        report.setResearchRunId(7L);
        report.setEvidenceCount(evidence);
        report.setSourceCount(sources);
        report.setContentMarkdown("# Research\nEvidence-backed conclusion");
        return report;
    }

    private ResearchRuntimeCheckpoint checkpoint(String status, int consumed, int max, int resumes) {
        ResearchRuntimeCheckpoint checkpoint = new ResearchRuntimeCheckpoint();
        checkpoint.setResearchRunId(7L);
        checkpoint.setStatus(status);
        checkpoint.setConsumedActions(consumed);
        checkpoint.setMaxActions(max);
        checkpoint.setResumeCount(resumes);
        checkpoint.setTerminationReason("COMPLETED".equals(status) ? "COMPLETED"
                : "TERMINATED".equals(status) ? "NO_PROGRESS" : null);
        return checkpoint;
    }

    private ResearchRuntimeEvent event(int sequence, String type, String node) {
        ResearchRuntimeEvent event = new ResearchRuntimeEvent();
        event.setSequenceNo(sequence);
        event.setEventType(type);
        event.setNodeId(node);
        return event;
    }
}
