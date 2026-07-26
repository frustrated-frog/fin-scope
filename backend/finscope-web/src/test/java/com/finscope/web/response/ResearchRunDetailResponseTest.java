package com.finscope.web.response;

import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.evaluation.ResearchEvaluation;
import com.finscope.domain.research.runtime.ResearchRuntimeView;
import com.finscope.domain.research.mission.ResearchMission;
import com.finscope.domain.research.mission.ResearchMissionView;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchRunDetailResponseTest {
    @Test
    void exposesRegenerationInsteadOfOpenCapabilityForLegacyRunWithoutReport() {
        ResearchRun run = run(15L, ResearchEnums.RUN_STATUS_PARTIAL_SUCCESS);

        ResearchRunDetailResponse response = new ResearchRunDetailResponse(run, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), null);

        assertFalse(response.isReportAvailable());
        assertTrue(response.isCanRegenerateReport());
        assertEquals(null, response.getReportStatus());
    }

    @Test
    void exposesPersistedReportMetadata() {
        ResearchRun run = run(16L, ResearchEnums.RUN_STATUS_PARTIAL_SUCCESS);
        ResearchReport report = new ResearchReport();
        report.setStatus("COMPLETED_WITH_GAPS");
        report.setGenerationMode("DETERMINISTIC");

        ResearchRunDetailResponse response = new ResearchRunDetailResponse(run, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), report);

        assertTrue(response.isReportAvailable());
        assertEquals("COMPLETED_WITH_GAPS", response.getReportStatus());
        assertEquals("DETERMINISTIC", response.getReportGenerationMode());
    }

    @Test
    void exposesRuntimeAndLatestEvaluationTelemetry() {
        ResearchRuntimeView runtime = new ResearchRuntimeView();
        runtime.setRecoverable(true);
        ResearchEvaluation evaluation = new ResearchEvaluation();
        evaluation.setScore(86);
        evaluation.setGateStatus("PASS");

        ResearchRunDetailResponse response = new ResearchRunDetailResponse(
                run(17L, ResearchEnums.RUN_STATUS_FAILED), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), null, runtime, evaluation);

        assertTrue(response.getRuntime().isRecoverable());
        assertEquals(86, response.getLatestEvaluation().getScore());
        assertEquals(null, response.getMission());
    }

    @Test
    void exposesOptionalMissionWithoutBreakingLegacyConstructor() {
        ResearchMission mission = new ResearchMission();
        mission.setResearchRunId(18L);
        mission.setGoal("验证AI资本开支能否持续");
        ResearchMissionView missionView = new ResearchMissionView();
        missionView.setMission(mission);

        ResearchRunDetailResponse response = new ResearchRunDetailResponse(
                run(18L, ResearchEnums.RUN_STATUS_RUNNING), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), null, null, null, missionView);

        assertEquals("验证AI资本开支能否持续", response.getMission().getMission().getGoal());
    }

    private ResearchRun run(Long id, String status) {
        ResearchRun run = new ResearchRun();
        run.setId(id);
        run.setThesisId(1L);
        run.setStatus(status);
        return run;
    }
}
