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
        RadarSignal third = signal("SSE", 1, 0.90D, now.minusMinutes(3));
        RadarEventSnapshot previous = new RadarEventSnapshot();
        previous.setSnapshotAt(now.minusMinutes(15));
        previous.setSignalCount(1);
        previous.setHotnessScore(42);

        RadarHotspotScoreService.Score score = service.score(Arrays.asList(first, second, third), now, previous);

        assertTrue(score.getTotalScore() >= 70);
        assertTrue(score.getVelocityScore() > 0.5D);
        assertEquals("RISING", score.getLifecycleState());
        assertTrue(score.getExplanation().contains("传播速度"));
        assertTrue(score.getExplanation().contains("市场反应/用户互动未接入"));
    }

    @Test
    void velocityDoesNotOverreactToShortProductionIntervals() {
        RadarSignal first = signal("CLS", 1, 0.95D, now.minusMinutes(5));
        RadarSignal second = signal("THS", 1, 0.80D, now.minusMinutes(8));
        RadarEventSnapshot previous = new RadarEventSnapshot();
        previous.setSnapshotAt(now.minusMinutes(5));
        previous.setSignalCount(1);
        previous.setHotnessScore(42);

        // 5 分钟间隔内仅新增 1 条信号：不应被放大成满分“爆发”，也不应进入 RISING。
        RadarHotspotScoreService.Score score = service.score(Arrays.asList(first, second), now, previous);

        assertTrue(score.getVelocityScore() <= 0.5D);
        assertTrue(score.getVelocityScore() > 0D);
        assertTrue(!"RISING".equals(score.getLifecycleState()));
    }

    @Test
    void fallsBackToFirstSeenInsteadOfTreatingRepeatedCollectionAsFresh() {
        RadarSignal old = signal("CLS", 1, 0.95D, null);
        old.setFirstSeenAt(now.minusDays(2));
        old.setLastSeenAt(now);

        RadarHotspotScoreService.Score score = service.score(Collections.singletonList(old), now);

        assertEquals(0D, score.getNoveltyScore(), 0.0001D);
    }

    @Test
    void appliesStrongDecayAfterSixHoursAndExpiresRecencyAtFortyEightHours() {
        double sixHours = service.score(Collections.singletonList(
                signal("CLS", 1, 0.95D, now.minusHours(6))), now).getNoveltyScore();
        double oneDay = service.score(Collections.singletonList(
                signal("CLS", 1, 0.95D, now.minusHours(24))), now).getNoveltyScore();
        double twoDays = service.score(Collections.singletonList(
                signal("CLS", 1, 0.95D, now.minusHours(48))), now).getNoveltyScore();

        assertTrue(sixHours <= 0.45D);
        assertTrue(oneDay <= 0.05D);
        assertEquals(0D, twoDays, 0.0001D);
    }

    @Test
    void freshSignalsGainAtLeastTwentyHotspotPointsOverOldReposts() {
        RadarHotspotScoreService.Score fresh = service.score(Arrays.asList(
                signal("CLS", 1, 0.95D, now.minusMinutes(20)),
                signal("THS", 2, 0.80D, now.minusMinutes(35))), now);
        RadarHotspotScoreService.Score old = service.score(Arrays.asList(
                signal("CLS", 1, 0.95D, now.minusDays(2)),
                signal("THS", 2, 0.80D, now.minusDays(2))), now);

        assertTrue(fresh.getTotalScore() - old.getTotalScore() >= 20);
    }

    @Test
    void copiedReportsDoNotCreateIndependentConfirmation() {
        RadarSignal first = detailedSignal("CLS", "财联社", 1, 0.75D, now.minusMinutes(10),
                "宁德时代发布新电池", "宁德时代发布新电池，能量密度提升20%");
        RadarSignal copied = detailedSignal("AGGREGATOR", "资讯聚合", 1, 0.75D, now.minusMinutes(8),
                "宁德时代发布新电池", "宁德时代发布新电池，能量密度提升20%");

        RadarHotspotScoreService.Score score = service.score(Arrays.asList(first, copied), now);

        assertEquals(1, score.getIndependentSourceCount());
        assertTrue(score.getConfirmationScore() < 0.5D);
        assertTrue(score.getConfidenceScore() < 70);
    }

    @Test
    void rewardsSourceRankImprovementAndPenalizesFallingRank() {
        RadarSignal rising = signal("CLS", 2, 0.80D, now.minusMinutes(10));
        rising.setPreviousSourceRank(12);
        RadarSignal falling = signal("CLS", 15, 0.80D, now.minusMinutes(10));
        falling.setPreviousSourceRank(2);

        RadarHotspotScoreService.Score risingScore = service.score(Collections.singletonList(rising), now);
        RadarHotspotScoreService.Score fallingScore = service.score(Collections.singletonList(falling), now);

        assertTrue(risingScore.getRankTrendScore() > fallingScore.getRankTrendScore());
        assertTrue(risingScore.getTotalScore() > fallingScore.getTotalScore());
    }

    private RadarSignal detailedSignal(String provider, String source, int rank, double weight,
                                       LocalDateTime publishedAt, String title, String content) {
        RadarSignal value = signal(provider, rank, weight, publishedAt);
        value.setSourceName(source);
        value.setTitle(title);
        value.setContent(content);
        value.setCategoryCode("COMPANY");
        return value;
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
