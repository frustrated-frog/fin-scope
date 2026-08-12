package com.finscope.service.radar;

import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.dedupe.FingerprintService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarHotspotReplayEvaluationTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 13, 10, 0);
    private final RadarHotspotScoreService scores = new RadarHotspotScoreService();
    private final RadarClusteringService clustering = new RadarClusteringService(
            new RadarTextAnalyzer(new FingerprintService()));

    @Test
    void independentOfficialConfirmationOutranksConcentratedReposts() {
        List<RadarSignal> confirmed = Arrays.asList(
                signal(1L, "CNINFO", "巨潮资讯", "OFFICIAL", "300750 公告电池扩产计划",
                        "300750公告电池扩产计划和量产时间", now.minusMinutes(15)),
                signal(2L, "CLS", "财联社", "TIER_1", "宁德时代电池项目扩产",
                        "财联社独立报道宁德时代新项目产能和投产日期", now.minusMinutes(10)));
        List<RadarSignal> reposts = Arrays.asList(
                signal(3L, "MEDIA_A", "媒体A", "TIER_2", "存储芯片板块快速上涨",
                        "存储芯片板块快速上涨，市场关注度提升", now.minusMinutes(5)),
                signal(4L, "MEDIA_B", "媒体B", "TIER_2", "存储芯片板块快速上涨",
                        "存储芯片板块快速上涨，市场关注度提升", now.minusMinutes(4)),
                signal(5L, "MEDIA_C", "媒体C", "TIER_2", "存储芯片板块快速上涨",
                        "存储芯片板块快速上涨，市场关注度提升", now.minusMinutes(3)));

        RadarHotspotScoreService.Score confirmedScore = scores.score(confirmed, now);
        RadarHotspotScoreService.Score repostScore = scores.score(reposts, now);

        assertEquals(2, confirmedScore.getIndependentSourceCount());
        assertEquals(1, repostScore.getIndependentSourceCount());
        assertTrue(confirmedScore.getConfidenceScore() > repostScore.getConfidenceScore());
        assertTrue(confirmedScore.getTotalScore() > repostScore.getTotalScore());
    }

    @Test
    void staleEventCannotBeManufacturedIntoHotspotByCopyingIt() {
        RadarSignal old = signal(1L, "MEDIA_A", "媒体A", "TIER_1", "两日前旧闻再次传播",
                "两日前旧闻再次传播", now.minusDays(2));
        RadarSignal copied = signal(2L, "MEDIA_B", "媒体B", "TIER_1", "两日前旧闻再次传播",
                "两日前旧闻再次传播", now.minusDays(2));

        RadarHotspotScoreService.Score result = scores.score(Arrays.asList(old, copied), now);

        assertEquals(1, result.getIndependentSourceCount());
        assertTrue(result.getTotalScore() < 40);
    }

    @Test
    void conflictAndStableIdentityRemainCorrectAcrossReplayBatches() {
        RadarSignal rising = signal(1L, "CLS", "财联社", "TIER_1", "宁德时代电池价格上涨",
                "宁德时代电池价格上涨", now.minusMinutes(20));
        RadarSignal falling = signal(2L, "THS", "同花顺", "TIER_1", "宁德时代电池价格下跌",
                "宁德时代电池价格下跌", now.minusMinutes(15));
        assertEquals(2, clustering.cluster(Arrays.asList(rising, falling)).size());

        RadarEventIdentityService identities = new RadarEventIdentityService(new RadarTextAnalyzer(new FingerprintService()));
        RadarSignal original = signal(3L, "CLS", "财联社", "TIER_1", "宁德时代发布新一代电池",
                "宁德时代发布新一代电池", now.minusMinutes(10));
        original.setCategoryCode("UNCLASSIFIED");
        RadarSignal rewritten = signal(4L, "THS", "同花顺", "TIER_1", "宁德时代新电池正式发布",
                "宁德时代新电池正式发布", now.minusMinutes(8));
        rewritten.setCategoryCode("COMPANY");

        assertEquals(identities.eventKey(Collections.singletonList(original)),
                identities.eventKey(Collections.singletonList(rewritten)));
        assertNotEquals(identities.eventKey(Collections.singletonList(rising)),
                identities.eventKey(Collections.singletonList(falling)));
    }

    private RadarSignal signal(Long id, String provider, String source, String tier,
                               String title, String content, LocalDateTime at) {
        RadarSignal value = new RadarSignal();
        value.setId(id);
        value.setItemId(provider + ":" + id);
        value.setProviderCode(provider);
        value.setSourceName(source);
        value.setSourceTier(tier);
        value.setCategoryCode("COMPANY");
        value.setTitle(title);
        value.setContent(content);
        value.setPublishedAt(at);
        value.setFirstSeenAt(at);
        value.setSourceRank(1);
        value.setSourceWeight("OFFICIAL".equals(tier) ? 1.0D : 0.8D);
        return value;
    }
}
