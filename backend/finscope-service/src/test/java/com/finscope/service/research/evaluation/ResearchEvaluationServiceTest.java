package com.finscope.service.research.evaluation;

import com.finscope.dao.research.ResearchReportRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.dao.research.ResearchRunOutputRepository;
import com.finscope.dao.research.ResearchSearchEvidenceRepository;
import com.finscope.dao.research.evaluation.ResearchEvaluationRepository;
import com.finscope.dao.research.runtime.ResearchRuntimeRepository;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchSearchEvidence;
import com.finscope.domain.research.evaluation.ResearchEvaluation;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.domain.research.runtime.ResearchRuntimeEvent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchEvaluationServiceTest {
    @Test
    void capturesStableInputFingerprintAndPersistsScoredEvaluation() {
        ResearchRunRepository runs = mock(ResearchRunRepository.class);
        ResearchReportRepository reports = mock(ResearchReportRepository.class);
        ResearchRuntimeRepository runtime = mock(ResearchRuntimeRepository.class);
        ResearchRunOutputRepository outputs = mock(ResearchRunOutputRepository.class);
        ResearchSearchEvidenceRepository searches = mock(ResearchSearchEvidenceRepository.class);
        ResearchEvaluationRepository evaluations = mock(ResearchEvaluationRepository.class);
        ResearchRun run = run();
        ResearchReport report = report();
        ResearchRuntimeCheckpoint checkpoint = checkpoint();
        when(runs.findById(7L)).thenReturn(Optional.of(run));
        when(reports.findByRunId(7L)).thenReturn(Optional.of(report));
        when(runtime.findCheckpoint(7L)).thenReturn(Optional.of(checkpoint));
        when(runtime.findEvents(7L)).thenReturn(Arrays.asList(event(1, "RUN_CREATED", null),
                event(2, "NODE_STARTED", "plan_sources"), event(3, "NODE_COMPLETED", "plan_sources"),
                event(4, "NODE_STARTED", "classify_events"), event(5, "NODE_COMPLETED", "classify_events"),
                event(6, "NODE_STARTED", "extract_evidence"), event(7, "NODE_COMPLETED", "extract_evidence"),
                event(8, "NODE_STARTED", "compose_report"), event(9, "NODE_COMPLETED", "compose_report"),
                event(10, "NODE_STARTED", "verify_output"), event(11, "NODE_COMPLETED", "verify_output"),
                event(12, "TERMINATED", "complete")));
        when(outputs.countByRunIdAndType(7L, "EVIDENCE")).thenReturn(4);
        when(outputs.findDistinctArticleSourceIdentities(7L)).thenReturn(Arrays.asList("one", "two", "three"));
        when(searches.findByRunId(7L)).thenReturn(Collections.<ResearchSearchEvidence>emptyList());
        when(evaluations.save(any(ResearchEvaluation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ResearchEvaluationService service = new ResearchEvaluationService(runs, reports, runtime, outputs, searches,
                evaluations, new ResearchEvaluationScorer());

        ResearchEvaluation first = service.evaluate(7L);
        ResearchEvaluation second = service.evaluate(7L);

        assertEquals(first.getInputFingerprint(), second.getInputFingerprint());
        assertEquals(64, first.getInputFingerprint().length());
        assertEquals("PASS", first.getGateStatus());
        verify(evaluations, org.mockito.Mockito.times(2)).save(any(ResearchEvaluation.class));
    }

    @Test
    void persistsBlockedEvaluationForLegacyRunWithoutRuntimeCheckpoint() {
        ResearchRunRepository runs = mock(ResearchRunRepository.class);
        ResearchReportRepository reports = mock(ResearchReportRepository.class);
        ResearchRuntimeRepository runtime = mock(ResearchRuntimeRepository.class);
        ResearchRunOutputRepository outputs = mock(ResearchRunOutputRepository.class);
        ResearchSearchEvidenceRepository searches = mock(ResearchSearchEvidenceRepository.class);
        ResearchEvaluationRepository evaluations = mock(ResearchEvaluationRepository.class);
        when(runs.findById(7L)).thenReturn(Optional.of(run()));
        when(reports.findByRunId(7L)).thenReturn(Optional.of(report()));
        when(runtime.findCheckpoint(7L)).thenReturn(Optional.empty());
        when(runtime.findEvents(7L)).thenReturn(java.util.Collections.<ResearchRuntimeEvent>emptyList());
        when(evaluations.save(any(ResearchEvaluation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(searches.findByRunId(7L)).thenReturn(Collections.<ResearchSearchEvidence>emptyList());
        when(outputs.findDistinctArticleSourceIdentities(7L)).thenReturn(Collections.<String>emptyList());
        ResearchEvaluationService service = new ResearchEvaluationService(runs, reports, runtime, outputs, searches,
                evaluations, new ResearchEvaluationScorer());

        ResearchEvaluation evaluation = service.evaluate(7L);

        assertEquals("BLOCK", evaluation.getGateStatus());
        org.junit.jupiter.api.Assertions.assertTrue(
                evaluation.getCriticalIssues().contains("MISSING_RUNTIME_CHECKPOINT"));
    }

    @Test
    void scoresRunScopedSearchEvidenceUsedByTheReport() {
        ResearchRunRepository runs = mock(ResearchRunRepository.class);
        ResearchReportRepository reports = mock(ResearchReportRepository.class);
        ResearchRuntimeRepository runtime = mock(ResearchRuntimeRepository.class);
        ResearchRunOutputRepository outputs = mock(ResearchRunOutputRepository.class);
        ResearchSearchEvidenceRepository searches = mock(ResearchSearchEvidenceRepository.class);
        ResearchEvaluationRepository evaluations = mock(ResearchEvaluationRepository.class);
        when(runs.findById(7L)).thenReturn(Optional.of(run()));
        when(reports.findByRunId(7L)).thenReturn(Optional.of(report()));
        when(runtime.findCheckpoint(7L)).thenReturn(Optional.of(checkpoint()));
        when(runtime.findEvents(7L)).thenReturn(Arrays.asList(event(1, "RUN_CREATED", null),
                event(2, "NODE_STARTED", "plan_sources"), event(3, "NODE_COMPLETED", "plan_sources"),
                event(4, "NODE_STARTED", "classify_events"), event(5, "NODE_COMPLETED", "classify_events"),
                event(6, "NODE_STARTED", "extract_evidence"), event(7, "NODE_COMPLETED", "extract_evidence"),
                event(8, "NODE_STARTED", "compose_report"), event(9, "NODE_COMPLETED", "compose_report"),
                event(10, "NODE_STARTED", "verify_output"), event(11, "NODE_COMPLETED", "verify_output"),
                event(12, "TERMINATED", "complete")));
        when(outputs.findDistinctArticleSourceIdentities(7L)).thenReturn(Collections.<String>emptyList());
        when(searches.findByRunId(7L)).thenReturn(Arrays.asList(
                search("a.example.com"), search("b.example.com"),
                search("c.example.com"), search("a.example.com")));
        when(evaluations.save(any(ResearchEvaluation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ResearchEvaluationService service = new ResearchEvaluationService(runs, reports, runtime, outputs,
                searches, evaluations, new ResearchEvaluationScorer());

        ResearchEvaluation evaluation = service.evaluate(7L);

        assertEquals("PASS", evaluation.getGateStatus());
        assertEquals(100, evaluation.getScore());
        assertEquals("deep-research-rules-v4", evaluation.getEvaluatorVersion());
        org.junit.jupiter.api.Assertions.assertFalse(
                evaluation.getCriticalIssues().contains("REPORT_OUTPUT_MISMATCH"));
    }

    private ResearchSearchEvidence search(String domain) {
        ResearchSearchEvidence value = new ResearchSearchEvidence();
        value.setSourceDomain(domain);
        value.setUrl("https://" + domain + "/evidence");
        return value;
    }

    private ResearchRun run() {
        ResearchRun run = new ResearchRun();
        run.setId(7L);
        run.setStatus(ResearchEnums.RUN_STATUS_COMPLETED);
        run.setEvidenceCount(4);
        run.setFetchedSourceCount(3);
        return run;
    }

    private ResearchReport report() {
        ResearchReport report = new ResearchReport();
        report.setId(11L);
        report.setResearchRunId(7L);
        report.setEvidenceCount(4);
        report.setSourceCount(3);
        report.setContentMarkdown("# report");
        return report;
    }

    private ResearchRuntimeCheckpoint checkpoint() {
        ResearchRuntimeCheckpoint checkpoint = new ResearchRuntimeCheckpoint();
        checkpoint.setResearchRunId(7L);
        checkpoint.setStateVersion(9);
        checkpoint.setStatus("COMPLETED");
        checkpoint.setConsumedActions(0);
        checkpoint.setMaxActions(12);
        checkpoint.setTerminationReason("COMPLETED");
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
