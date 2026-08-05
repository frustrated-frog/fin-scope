package com.finscope.service.radar;

import com.finscope.domain.radar.RadarSignal;
import com.finscope.domain.radar.RadarEventSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarHotspotScoreServiceTest {
    private final RadarHotspotScoreService service = new RadarHotspotScoreService();
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 0);

    @Test
    void rewardsFreshTopRankedMultiSourceSignals() {
        RadarSignal first = signal("CLS", 1, 0.95D, now.minusMinutes(20));
        RadarSignal second = signal("THS", 2, 0.80D, now.minusMinutes(35));

        RadarHotspotScoreService.Score score = service.score(Arrays.asList(first, second), now);

        assertTrue(score.getTotalScore() >= 75);
        assertTrue(score.getExplanation().contains("多源"));
    }

    @Test
    void keepsEmptyAndSingleSignalScoresBounded() {
        assertEquals(0, service.score(Collections.<RadarSignal>emptyList(), now).getTotalScore());

        RadarHotspotScoreService.Score score = service.score(
                Collections.singletonList(signal("CLS", 12, 0.55D, now.minusDays(2))), now);

        assertTrue(score.getTotalScore() >= 0 && score.getTotalScore() <= 100);
    }

    @Test
    void scoresPrdHotnessWithVelocityAndKeepsLifecycleVisible() {
        RadarSignal first = signal("CLS", 1, 0.95D, now.minusMinutes(5));
        RadarSignal second = signal("THS", 1, 0.80D, now.minusMinutes(8));
        RadarEventSnapshot previous = new RadarEventSnapshot();
        previous.setSnapshotAt(now.minusMinutes(15));
        previous.setSignalCount(1);
        previous.setHotnessScore(42);

        RadarHotspotScoreService.Score score = service.score(Arrays.asList(first, second), now, previous);

        assertTrue(score.getTotalScore() >= 70);
        assertTrue(score.getVelocityScore() > 0.5D);
        assertEquals("RISING", score.getLifecycleState());
        assertTrue(score.getExplanation().contains("传播速度"));
        assertTrue(score.getExplanation().contains("市场反应/用户互动未接入"));
    }

    private RadarSignal signal(String provider, int rank, double weight, LocalDateTime publishedAt) {
        RadarSignal value = new RadarSignal();
        value.setProviderCode(provider);
        value.setSourceRank(rank);
        value.setSourceWeight(weight);
        value.setPublishedAt(publishedAt);
        value.setSourceTier("TIER_1");
        return value;
    }
}
