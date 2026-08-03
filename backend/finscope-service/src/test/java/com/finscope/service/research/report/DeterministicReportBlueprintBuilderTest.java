package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicReportBlueprintBuilderTest {
    @Test
    void buildsACompleteDynamicBlueprintAndUsesAvailableCounterEvidence() {
        ResearchThesis thesis = new ResearchThesis();
        thesis.setSubjectName("长鑫科技");
        thesis.setSubjectType("COMPANY");
        thesis.setQuestion("长鑫科技上市两日的市值、成交和换手说明什么？");
        List<ResearchEvidenceDossier> dossier = Arrays.asList(
                evidence("E1", "SUPPORT", "交易所披露首日成交额和换手率处于高位。"),
                evidence("E2", "SUPPORT", "公司公告说明发行后自由流通股份占总股本比例有限。"),
                evidence("E3", "COUNTER", "次日成交缩量且价格波动扩大，短期分歧仍然明显。"),
                evidence("E4", "NEUTRAL", "总市值口径包含暂不可自由交易股份。"),
                evidence("E5", "NEUTRAL", "同行可比公司估值口径与公司存在差异。"),
                evidence("E6", "SUPPORT", "上市公告披露募集资金用途和产能规划。"));

        ResearchReportBlueprint result = new DeterministicReportBlueprintBuilder().build(thesis, dossier, true);

        assertTrue(result.getKeyInsights().size() >= 3 && result.getKeyInsights().size() <= 6);
        assertTrue(result.getSubQuestions().size() >= 3 && result.getSubQuestions().size() <= 6);
        assertTrue(result.getArgumentChains().size() >= 2);
        assertEquals(3, result.getScenarios().size());
        assertTrue(result.getWatchItems().size() >= 3 && result.getWatchItems().size() <= 8);
        assertTrue(result.getSubQuestions().stream().anyMatch(item -> item.getQuestion().contains("流通")
                || item.getQuestion().contains("交易")));
        assertTrue(result.getStrongestCounterargument().getEvidenceRefs().contains("E3"));

        Set<String> allowed = new HashSet<String>(Arrays.asList("E1", "E2", "E3", "E4", "E5", "E6"));
        for (ResearchReportBlueprint.KeyInsight item : result.getKeyInsights()) {
            assertFalse(item.getEvidenceRefs().isEmpty());
            assertTrue(allowed.containsAll(item.getEvidenceRefs()));
        }
        for (ResearchReportBlueprint.ArgumentChain item : result.getArgumentChains()) {
            assertFalse(item.getEvidenceRefs().isEmpty());
            assertTrue(allowed.containsAll(item.getEvidenceRefs()));
        }
    }

    private ResearchEvidenceDossier evidence(String ref, String stance, String fact) {
        return new ResearchEvidenceDossier(ref, Long.valueOf(ref.substring(1)), null,
                "source-" + ref, "来源" + ref, "T1", "标题" + ref,
                LocalDateTime.of(2026, 7, 29, 10, 0), "https://example.com/" + ref,
                fact, stance, 90 - Integer.parseInt(ref.substring(1)));
    }
}
