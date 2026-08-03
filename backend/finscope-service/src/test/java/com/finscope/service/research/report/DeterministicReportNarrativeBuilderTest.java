package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicReportNarrativeBuilderTest {
    @Test
    void buildsCompleteObjectSpecificNarrativeForEveryBlueprintSlot() {
        ResearchThesis thesis = new ResearchThesis();
        thesis.setSubjectName("微软");
        thesis.setSubjectType("COMPANY");
        thesis.setQuestion("微软云业务增长是否足以覆盖AI资本开支？");
        List<ResearchEvidenceDossier> dossier = Arrays.asList(
                evidence("E1", "SUPPORT", "微软披露Azure收入增长，云业务需求保持增长。"),
                evidence("E2", "COUNTER", "微软资本开支增加，自由现金流仍需后续披露验证。"),
                evidence("E3", "NEUTRAL", "管理层给出下一季度云业务指引。"));
        ResearchReportBlueprint blueprint = new DeterministicReportBlueprintBuilder().build(thesis, dossier, true);

        ResearchReportNarrative result = new DeterministicReportNarrativeBuilder().build(thesis, blueprint, dossier);

        assertTrue(result.getExecutiveSummary().contains("微软"));
        assertTrue(result.getWhatHappened().contains("E1"));
        assertEquals(blueprint.getSubQuestions().size(), result.getSubQuestionAnalysis().size());
        assertEquals(blueprint.getArgumentChains().size(), result.getArgumentAnalysis().size());
        assertEquals(blueprint.getScenarios().size(), result.getScenarioAnalysis().size());
        assertFalse(result.getCounterAnalysis().trim().isEmpty());
        assertFalse(result.getKnowledgeSynthesis().trim().isEmpty());
        assertFalse(result.getMonitoringPlan().trim().isEmpty());
        assertFalse(result.getExecutiveSummary().contains("晶圆厂"));
    }

    private ResearchEvidenceDossier evidence(String ref, String stance, String fact) {
        return new ResearchEvidenceDossier(ref, Long.valueOf(ref.substring(1)), null,
                "source-" + ref, "来源" + ref, "T1", "标题" + ref,
                LocalDateTime.of(2026, 7, 29, 10, 0), "https://example.com/" + ref,
                fact, stance, 90 - Integer.parseInt(ref.substring(1)));
    }
}
