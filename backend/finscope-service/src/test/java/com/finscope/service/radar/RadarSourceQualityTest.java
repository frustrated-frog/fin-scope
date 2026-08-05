package com.finscope.service.radar;

import com.finscope.domain.radar.RadarSignal;
import com.finscope.domain.radar.RadarEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarSourceQualityTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 0);

    @Test
    void normalizesEquivalentHighQualityTierNames() {
        assertEquals(RadarSourceQuality.TIER_1, RadarSourceQuality.resolve("T1"));
        assertEquals(RadarSourceQuality.TIER_1, RadarSourceQuality.resolve("TIER_1"));
        assertEquals(RadarSourceQuality.TIER_1, RadarSourceQuality.resolve("PRIMARY"));
        assertEquals(RadarSourceQuality.TIER_1, RadarSourceQuality.resolve("OFFICIAL"));
    }

    @Test
    void normalizesProviderStyleMiddleTierWithoutFallingBackToUnknown() {
        assertEquals(RadarSourceQuality.TIER_2, RadarSourceQuality.resolve("T2"));
        assertEquals(RadarSourceQuality.TIER_2, RadarSourceQuality.resolve("TIER_2"));
        assertEquals(RadarSourceQuality.TIER_3, RadarSourceQuality.resolve("T3"));
        assertEquals(RadarSourceQuality.TIER_3, RadarSourceQuality.resolve("UNKNOWN"));
    }

    @Test
    void realProviderTierGetsMoreHotnessThanUnknownTier() {
        RadarSignal providerSignal = signal("T2");
        RadarSignal unknownSignal = signal("UNKNOWN");
        RadarHotspotScoreService service = new RadarHotspotScoreService();

        int providerScore = service.score(Collections.singletonList(providerSignal), now).getTotalScore();
        int unknownScore = service.score(Collections.singletonList(unknownSignal), now).getTotalScore();

        assertTrue(providerScore > unknownScore);
    }

    @Test
    void providerStyleMiddleTierKeepsResearchPriorityPoints() {
        RadarEvent event = new RadarEvent();
        event.setCanonicalTitle("测试事件"); event.setFirstSeenAt(now); event.setLastSeenAt(now);
        RadarSignal signal = signal("T2");

        assertEquals(10, new RadarPriorityService().score(event,
                Collections.singletonList(signal), Collections.emptyList(), now).getSourceQualityScore());
    }

    private RadarSignal signal(String tier) {
        RadarSignal value = new RadarSignal();
        value.setProviderCode("CLS");
        value.setSourceTier(tier);
        value.setSourceRank(1);
        value.setPublishedAt(now.minusMinutes(10));
        return value;
    }
}
