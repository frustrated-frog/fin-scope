package com.finscope.service.research.report;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchClaimAuditorTest {
    private final ResearchClaimAuditor auditor = new ResearchClaimAuditor(new ResearchClaimExtractor());

    @Test
    void supportsClaimWhenBoundEvidenceContainsItsNumberDateAndSubject() {
        String report = "## 核心结论\n\n公司在2025年收入增长18%，研发投入同步增加。[E1]";

        ResearchClaimAudit audit = auditor.audit(report, Collections.singletonList(
                evidence("E1", "公司披露2025年收入同比增长18%，研发投入同比增加。")));

        assertEquals(1, audit.getClaimCount());
        assertEquals(1, audit.getSupportedCount());
        assertEquals("SUPPORTED", audit.getItems().get(0).getStatus());
    }

    @Test
    void rejectsUncitedNumberAndCitationThatDoesNotContainClaimedDate() {
        String report = "## 核心结论\n\n公司收入增长25%。公司在2026年完成上市。[E1]";

        ResearchClaimAudit audit = auditor.audit(report, Collections.singletonList(
                evidence("E1", "公司于2025年完成上市。")));

        assertEquals(2, audit.getUnsupportedCount());
        assertTrue(audit.hasBlockingIssues());
    }

    @Test
    void distinguishesPartialSupportAndConflictingFigures() {
        String report = "## 核心结论\n\n公司需求改善但盈利能力已全面恢复。[E1]\n\n"
                + "公司在2025年收入增长18%。[E2][E3]";

        ResearchClaimAudit audit = auditor.audit(report, Arrays.asList(
                evidence("E1", "公司订单需求出现改善，但利润率仍低于历史水平。"),
                evidence("E2", "公司披露2025年收入同比增长18%。"),
                evidence("E3", "另一份材料称公司2025年收入同比增长12%。")));

        assertEquals(1, audit.getPartialCount());
        assertEquals(1, audit.getConflictCount());
    }

    private ResearchEvidenceDossier evidence(String ref, String excerpt) {
        return new ResearchEvidenceDossier(ref, null, null, "example.com", "示例来源", "T2",
                "示例材料", null, "https://example.com/" + ref, excerpt, "SUPPORT", 90);
    }
}
