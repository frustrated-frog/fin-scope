package com.finscope.service.attribution;

import java.time.LocalDate;
import com.finscope.domain.attribution.AttributionEvidence;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AttributionEvidenceGateTest {
    @Test
    void removesTrackingUrlDuplicatesAndCapsConfidenceByEvidenceQuality() {
        AttributionEvidence lowQuality = evidence("https://example.com/news?id=1&utm_source=x", "T3", "INDIRECT");
        AttributionEvidence duplicate = evidence("https://example.com/news?id=1&utm_source=y", "T3", "INDIRECT");
        AttributionEvidence directAuthority = evidence("https://www.sse.com.cn/notice/1", "T1", "DIRECT");

        AttributionEvidenceGate gate = new AttributionEvidenceGate();
        List<AttributionEvidence> normalized = gate.normalizeAndRank(Arrays.asList(lowQuality, duplicate, directAuthority));

        assertEquals(2, normalized.size());
        assertEquals("LOW", gate.capConfidence("HIGH", Arrays.asList(lowQuality), LocalDate.parse("2026-09-18")));
        assertEquals("MID", gate.capConfidence("HIGH", Arrays.asList(directAuthority), LocalDate.parse("2026-09-18")));
    }

    @Test
    void excludesFutureEvidenceAndTreatsEarlierDatesAsBackground() {
        AttributionEvidence current = evidence("https://a.com/current", "T1", "DIRECT");
        AttributionEvidence future = evidence("https://a.com/future", "T1", "DIRECT");
        future.setPublishedAt("2026-09-19T08:00:00+08:00");
        AttributionEvidence past = evidence("https://a.com/past", "T1", "DIRECT");
        past.setPublishedAt("2026-09-17");
        AttributionEvidence unknown = evidence("https://a.com/unknown", "T1", "DIRECT");
        unknown.setPublishedAt("yesterday");
        AttributionEvidenceGate gate = new AttributionEvidenceGate();
        LocalDate date = LocalDate.parse("2026-09-18");
        List<AttributionEvidence> eligible = gate.eligibleAtDate(Arrays.asList(current, future, past, unknown), date);

        assertEquals(3, eligible.size());
        assertFalse(eligible.contains(future));
        assertTrue(past.isHistoricalContext());
        assertEquals("LOW", gate.capConfidence("HIGH", Arrays.asList(past, unknown, future), date));
        assertEquals("LOW", gate.capConfidence("HIGH", Arrays.asList(current), null));
    }

    @Test
    void counterAndBackgroundCannotRaiseConfidenceEvenWithAuthoritySources() {
        AttributionEvidence authority = evidence("https://a.com/notice", "T1", "DIRECT");
        AttributionEvidence confirmation = evidence("https://b.com/news", "T2", "INDIRECT");
        AttributionEvidenceGate gate = new AttributionEvidenceGate();
        LocalDate date = LocalDate.parse("2026-09-18");
        assertEquals("HIGH", gate.capConfidence("HIGH", Arrays.asList(authority, confirmation), date));
        confirmation.setStance("COUNTER");
        assertEquals("LOW", gate.capConfidence("HIGH", Arrays.asList(authority, confirmation), date));
        assertEquals("LOW", gate.capConfidence("MID", Arrays.asList(confirmation), date));
        confirmation.setStance("BACKGROUND");
        assertEquals("MID", gate.capConfidence("HIGH", Arrays.asList(authority, confirmation), date));
        confirmation.setStance("SUPPORT");
        confirmation.setUrl("https://www.a.com/reprint");
        assertEquals("MID", gate.capConfidence("HIGH", Arrays.asList(authority, confirmation), date));
        authority.setUrl("malformed-url");
        confirmation.setUrl(null);
        assertEquals("LOW", gate.capConfidence("HIGH", Arrays.asList(authority, confirmation), date));
    }

    @Test
    void deduplicationDoesNotLoseCounterEvidence() {
        AttributionEvidence support = evidence("https://a.com/notice", "T1", "DIRECT");
        AttributionEvidence counter = evidence("https://a.com/notice?utm_source=x", "T2", "INDIRECT");
        counter.setStance("COUNTER");
        AttributionEvidenceGate gate = new AttributionEvidenceGate();
        List<AttributionEvidence> normalized = gate.normalizeAndRank(Arrays.asList(support, counter));
        assertEquals(1, normalized.size());
        assertEquals("COUNTER", normalized.get(0).getStance());
        assertEquals("LOW", gate.capConfidence("HIGH", normalized, LocalDate.parse("2026-09-18")));
    }

    private AttributionEvidence evidence(String url, String tier, String directness) {
        AttributionEvidence evidence = new AttributionEvidence();
        evidence.setUrl(url);
        evidence.setTitle("测试证据 " + url);
        evidence.setSourceTier(tier);
        evidence.setDirectness(directness);
        evidence.setRelevance(70);
        evidence.setPublishedAt("2026-09-18");
        evidence.setStance("SUPPORT");
        return evidence;
    }
}
