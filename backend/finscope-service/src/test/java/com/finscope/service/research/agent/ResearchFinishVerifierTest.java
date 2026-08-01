package com.finscope.service.research.agent;

import com.finscope.dao.research.mission.ResearchMissionRepository;
import com.finscope.dao.research.runtime.ResearchRuntimeRepository;
import com.finscope.domain.research.mission.ResearchMission;
import com.finscope.domain.research.mission.ResearchMissionTask;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.service.research.report.EvidenceSufficiency;
import com.finscope.service.research.report.ResearchReportService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResearchFinishVerifierTest {
    @Test
    void acceptsOnlySufficientConsistentResearchState() {
        ResearchReportService reports = mock(ResearchReportService.class);
        ResearchMissionRepository missions = mock(ResearchMissionRepository.class);
        ResearchRuntimeRepository runtimes = mock(ResearchRuntimeRepository.class);
        ResearchFinishVerifier verifier = new ResearchFinishVerifier(reports, missions, runtimes);
        when(reports.assessSufficiency(71L)).thenReturn(EvidenceSufficiency.fromCounts(8, 4, 5, 3));
        when(missions.findMission(71L)).thenReturn(Optional.of(mission(null)));
        when(runtimes.findCheckpoint(71L)).thenReturn(Optional.of(runtime("RUNNING")));

        ResearchFinishVerdict verdict = verifier.verify(71L);

        assertTrue(verdict.isAccepted());
        assertEquals("ACCEPTED", verdict.getReasonCode());
    }

    @Test
    void returnsEvidenceAndActiveTaskReasonsInsteadOfEndingEarly() {
        ResearchReportService reports = mock(ResearchReportService.class);
        ResearchMissionRepository missions = mock(ResearchMissionRepository.class);
        ResearchRuntimeRepository runtimes = mock(ResearchRuntimeRepository.class);
        ResearchFinishVerifier verifier = new ResearchFinishVerifier(reports, missions, runtimes);
        when(reports.assessSufficiency(71L)).thenReturn(EvidenceSufficiency.fromCounts(4, 1, 4, 0));
        when(missions.findMission(71L)).thenReturn(Optional.of(mission("search_counter")));
        when(runtimes.findCheckpoint(71L)).thenReturn(Optional.of(runtime("RUNNING")));

        ResearchFinishVerdict verdict = verifier.verify(71L);

        assertFalse(verdict.isAccepted());
        assertEquals("EVIDENCE_INSUFFICIENT", verdict.getReasonCode());
        assertTrue(verdict.getMissingConditions().contains("独立来源不足 2 个"));
        assertTrue(verdict.getMissingConditions().contains("仍有研究任务正在执行：search_counter"));
    }

    @Test
    void rejectsFinishUntilEverySelectedMethodIntentIsTerminal() {
        ResearchReportService reports = mock(ResearchReportService.class);
        ResearchMissionRepository missions = mock(ResearchMissionRepository.class);
        ResearchRuntimeRepository runtimes = mock(ResearchRuntimeRepository.class);
        ResearchFinishVerifier verifier = new ResearchFinishVerifier(reports, missions, runtimes);
        ResearchMission mission = mission(null);
        mission.setMethodCodes(Collections.singletonList("FINANCIAL_STATEMENT_QUALITY"));
        when(reports.assessSufficiency(71L)).thenReturn(EvidenceSufficiency.fromCounts(8, 4, 5, 3));
        when(missions.findMission(71L)).thenReturn(Optional.of(mission));
        when(missions.findTasks(71L)).thenReturn(Arrays.asList(
                task("PRIMARY", "COMPLETED"), task("SUPPORT", "COMPLETED"),
                task("COUNTER", "PENDING"), task("ASSESS", "COMPLETED")));
        when(runtimes.findCheckpoint(71L)).thenReturn(Optional.of(runtime("RUNNING")));

        ResearchFinishVerdict verdict = verifier.verify(71L);

        assertFalse(verdict.isAccepted());
        assertEquals("METHOD_INCOMPLETE", verdict.getReasonCode());
        assertTrue(verdict.getMissingConditions().contains(
                "投研方法 FINANCIAL_STATEMENT_QUALITY 尚未完成 COUNTER 意图"));
    }

    @Test
    void acceptsCompletedOrEvidenceSkippedMethodIntents() {
        ResearchReportService reports = mock(ResearchReportService.class);
        ResearchMissionRepository missions = mock(ResearchMissionRepository.class);
        ResearchRuntimeRepository runtimes = mock(ResearchRuntimeRepository.class);
        ResearchFinishVerifier verifier = new ResearchFinishVerifier(reports, missions, runtimes);
        ResearchMission mission = mission(null);
        mission.setMethodCodes(Collections.singletonList("FINANCIAL_STATEMENT_QUALITY"));
        when(reports.assessSufficiency(71L)).thenReturn(EvidenceSufficiency.fromCounts(8, 4, 5, 3));
        when(missions.findMission(71L)).thenReturn(Optional.of(mission));
        when(missions.findTasks(71L)).thenReturn(Arrays.asList(
                task("PRIMARY", "COMPLETED"), task("SUPPORT", "SKIPPED"),
                task("COUNTER", "COMPLETED"), task("ASSESS", "COMPLETED")));
        when(runtimes.findCheckpoint(71L)).thenReturn(Optional.of(runtime("RUNNING")));

        assertTrue(verifier.verify(71L).isAccepted());
    }

    private ResearchMission mission(String activeTask) {
        ResearchMission value = new ResearchMission();
        value.setResearchRunId(71L);
        value.setActiveTaskKey(activeTask);
        return value;
    }

    private ResearchRuntimeCheckpoint runtime(String status) {
        ResearchRuntimeCheckpoint value = new ResearchRuntimeCheckpoint();
        value.setResearchRunId(71L);
        value.setStatus(status);
        return value;
    }

    private ResearchMissionTask task(String intent, String status) {
        ResearchMissionTask value = new ResearchMissionTask();
        value.setIntent(intent);
        value.setStatus(status);
        return value;
    }
}
