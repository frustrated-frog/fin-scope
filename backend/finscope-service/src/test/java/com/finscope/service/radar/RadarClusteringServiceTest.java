package com.finscope.service.radar;

import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.dedupe.FingerprintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarClusteringServiceTest {
    private RadarClusteringService service;

    @BeforeEach
    void setUp() {
        service = new RadarClusteringService(new RadarTextAnalyzer(new FingerprintService()));
    }

    @Test
    void mergesParaphrasesAboutSameSubjectAndActionAcrossSources() {
        RadarClusteringService.MatchDecision decision = service.decide(
                signal(1L, "CLS", "宁德时代发布新一代电池", "COMPANY"),
                signal(2L, "THS", "宁德时代新电池正式发布", "COMPANY"));

        assertEquals("SAME", decision.getReasonCode());
        assertTrue(decision.getScore() >= 0.78);
    }

    @Test
    void rejectsSimilarActionsWhenPrimarySubjectsConflict() {
        RadarClusteringService.MatchDecision decision = service.decide(
                signal(1L, "CLS", "宁德时代发布新一代电池", "COMPANY"),
                signal(2L, "THS", "小米发布新款汽车", "COMPANY"));

        assertEquals("DIFFERENT_SUBJECT", decision.getReasonCode());
    }

    @Test
    void keepsDifferentPolicyActorsInSeparateEvents() {
        RadarClusteringService.MatchDecision decision = service.decide(
                signal(1L, "CLS", "美联储宣布维持利率不变", "MACRO_POLICY"),
                signal(2L, "THS", "中国央行开展逆回购操作", "MACRO_POLICY"));

        assertNotEquals("SAME", decision.getReasonCode());
    }

    @Test
    void ambiguousPairIsConservativelySplitWithoutLlm() {
        RadarClusteringService.MatchDecision decision = service.decide(
                signal(1L, "CLS", "存储芯片板块持续走强", "MARKET_MOVE"),
                signal(2L, "THS", "芯片股午后集体上涨", "MARKET_MOVE"));

        assertEquals("AMBIGUOUS", decision.getReasonCode());
    }

    @Test
    void clusteringBuildsStableCrossSourceGroup() {
        List<RadarClusteringService.ClusterResult> clusters = service.cluster(Arrays.asList(
                signal(1L, "CLS", "宁德时代发布新一代电池", "COMPANY"),
                signal(2L, "THS", "宁德时代新电池正式发布", "COMPANY"),
                signal(3L, "CLS", "小米发布新款汽车", "COMPANY")));

        assertEquals(2, clusters.size());
        assertEquals(2, clusters.get(0).getSignals().size());
        assertEquals("宁德时代发布新一代电池", clusters.get(0).getEvent().getCanonicalTitle());
        assertEquals(2, clusters.get(0).getEvent().getSourceCount());
        assertEquals("主体、动作和标题语义一致", clusters.get(0).getLinks().get(1).getMatchReason());
    }

    private RadarSignal signal(Long id, String provider, String title, String category) {
        RadarSignal signal = new RadarSignal();
        signal.setId(id);
        signal.setItemId(provider + ":" + id);
        signal.setProviderCode(provider);
        signal.setSourceName(provider);
        signal.setSourceTier("TIER_1");
        signal.setTitle(title);
        signal.setContent(title + "，更多信息正在更新。");
        signal.setCategoryCode(category);
        signal.setPublishedAt(LocalDateTime.of(2026, 7, 31, 14, 0).plusMinutes(id));
        signal.setFirstSeenAt(signal.getPublishedAt());
        signal.setLastSeenAt(signal.getPublishedAt());
        return signal;
    }
}
