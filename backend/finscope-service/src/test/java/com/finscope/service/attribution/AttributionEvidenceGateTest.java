package com.finscope.service.attribution;

import com.finscope.domain.attribution.AttributionEvidence;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttributionEvidenceGateTest {
    @Test
    void removesTrackingUrlDuplicatesAndCapsConfidenceByEvidenceQuality() {
        AttributionEvidence lowQuality = evidence("https://example.com/news?id=1&utm_source=x", "T3", "INDIRECT");
        AttributionEvidence duplicate = evidence("https://example.com/news?id=1&utm_source=y", "T3", "INDIRECT");
        AttributionEvidence directAuthority = evidence("https://www.sse.com.cn/notice/1", "T1", "DIRECT");

        AttributionEvidenceGate gate = new AttributionEvidenceGate();
        List<AttributionEvidence> normalized = gate.normalizeAndRank(Arrays.asList(lowQuality, duplicate, directAuthority));

        assertEquals(2, normalized.size());
        assertEquals("LOW", gate.capConfidence("HIGH", Arrays.asList(lowQuality)));
        assertEquals("MID", gate.capConfidence("HIGH", Arrays.asList(directAuthority)));
    }

    private AttributionEvidence evidence(String url, String tier, String directness) {
        AttributionEvidence evidence = new AttributionEvidence();
        evidence.setUrl(url);
        evidence.setTitle("测试证据 " + url);
        evidence.setSourceTier(tier);
        evidence.setDirectness(directness);
        evidence.setRelevance(70);
        return evidence;
    }
}
