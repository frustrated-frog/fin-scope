package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarSignal;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarEvidenceTrustServiceTest {
    private final RadarEvidenceTrustService service = new RadarEvidenceTrustService();

    @Test
    void countsNormalizedSourcesCitationsAndNumericConflicts() {
        RadarSignal first = signal(1L, "财联社", "TIER_1", "https://cls.cn/a", "预计增长20%");
        RadarSignal sameHost = signal(2L, "财联社快讯", "TIER_1", "https://cls.cn/b", "预计增长20%");
        RadarEvidence second = evidence(31L, "公司公告", "TIER_1", "https://cninfo.com.cn/x", "预计增长30%");
        RadarEventInterpretation.Result result = new RadarEventInterpretation.Result();
        result.setEvidenceRefs(Arrays.asList("signal:1", "evidence:31", "missing:7"));
        RadarEventInterpretation interpretation = new RadarEventInterpretation(); interpretation.setResult(result);

        RadarEventWorkspace.Trust trust = service.assess(Arrays.asList(first, sameHost),
                Collections.singletonList(second), interpretation);

        assertEquals(2, trust.getIndependentSourceCount());
        assertEquals(2, trust.getCitationCoveredCount());
        assertEquals(3, trust.getCitationTotalCount());
        assertEquals(Integer.valueOf(3), trust.getSourceTierCounts().get("TIER_1"));
        assertTrue(trust.getConflicts().get(0).contains("20%"));
        assertTrue(trust.getConflicts().get(0).contains("30%"));
    }

    private RadarSignal signal(Long id, String source, String tier, String url, String content) {
        RadarSignal value = new RadarSignal(); value.setId(id); value.setSourceName(source); value.setSourceTier(tier);
        value.setUrl(url); value.setTitle(content); value.setContent(content); return value;
    }
    private RadarEvidence evidence(Long id, String source, String tier, String url, String summary) {
        RadarEvidence value = new RadarEvidence(); value.setId(id); value.setSourceName(source); value.setSourceTier(tier);
        value.setUrl(url); value.setTitle(summary); value.setSummary(summary); return value;
    }
}
