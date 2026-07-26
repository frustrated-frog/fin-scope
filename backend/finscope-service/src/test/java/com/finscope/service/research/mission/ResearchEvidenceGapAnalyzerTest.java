package com.finscope.service.research.mission;

import com.finscope.domain.research.mission.ResearchMissionGap;
import com.finscope.service.research.report.EvidenceSufficiency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchEvidenceGapAnalyzerTest {
    private final ResearchEvidenceGapAnalyzer analyzer = new ResearchEvidenceGapAnalyzer();

    @Test
    void prioritizesMissingCounterEvidenceBeforeBreadth() {
        ResearchMissionGap gap = analyzer.assess(7L, "baseline_scan",
                EvidenceSufficiency.fromCounts(4, 2, 4, 0));

        assertFalse(gap.isSufficient());
        assertEquals("COUNTER", gap.getRecommendedIntent());
        assertTrue(gap.getWarnings().contains("缺少反向或风险证据，结论可能存在单边偏差"));
        assertEquals(64, gap.getStateHash().length());
    }

    @Test
    void recommendsPrimarySourcesWhenDirectionsExistButSourceDiversityIsLow() {
        ResearchMissionGap gap = analyzer.assess(7L, "search_counter",
                EvidenceSufficiency.fromCounts(6, 1, 4, 2));

        assertEquals("PRIMARY", gap.getRecommendedIntent());
    }

    @Test
    void returnsStableSufficientSnapshot() {
        EvidenceSufficiency sufficiency = EvidenceSufficiency.fromCounts(7, 3, 5, 2);

        ResearchMissionGap first = analyzer.assess(7L, "search_primary", sufficiency);
        ResearchMissionGap second = analyzer.assess(7L, "search_primary", sufficiency);

        assertTrue(first.isSufficient());
        assertEquals("NONE", first.getRecommendedIntent());
        assertEquals(first.getStateHash(), second.getStateHash());
    }
}
