package com.finscope.service.radar;

import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.dedupe.FingerprintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
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

    @Test
    void eventIdentityDoesNotChangeWhenTheSameTitleGetsADifferentSourceCategory() {
        RadarTextAnalyzer analyzer = new RadarTextAnalyzer(new FingerprintService());
        RadarSignal unclassified = signal(1L, "CLS", "港股人工智能股走弱 兆易创新跌超5%", "UNCLASSIFIED");
        RadarSignal marketMove = signal(2L, "CLS", "港股人工智能股走弱 兆易创新跌超5%", "MARKET_MOVE");

        assertEquals(analyzer.eventKey(analyzer.analyze(unclassified)),
                analyzer.eventKey(analyzer.analyze(marketMove)));
    }

    @Test
    void ambiguousPairRemainsSplitWithoutAgentInput() {
        RadarClusteringService deterministicClustering = new RadarClusteringService(
                new RadarTextAnalyzer(new FingerprintService()));

        List<RadarClusteringService.ClusterResult> clusters = deterministicClustering.cluster(Arrays.asList(
                signal(1L, "CLS", "存储芯片板块持续走强", "MARKET_MOVE"),
                signal(2L, "THS", "芯片股午后集体上涨", "MARKET_MOVE")));

        assertEquals(2, clusters.size());
    }

    @Test
    void graphClusteringKeepsIndirectlyConnectedSignalsTogether() {
        RadarClusteringService graphClustering = new RadarClusteringService(
                new RadarTextAnalyzer(new FingerprintService())) {
            @Override
            public MatchDecision decide(RadarSignal left, RadarSignal right) {
                if (left.getId() == 1L && right.getId() == 2L
                        || left.getId() == 2L && right.getId() == 3L) {
                    return new MatchDecision("SAME", 0.86D, "确定性相邻事实边");
                }
                return new MatchDecision("DIFFERENT", 0.20D, "确定性事实不同");
            }
        };

        List<RadarClusteringService.ClusterResult> clusters = graphClustering.cluster(Arrays.asList(
                signal(1L, "A", "存储芯片板块持续走强", "MARKET_MOVE"),
                signal(2L, "B", "芯片股午后集体上涨", "MARKET_MOVE"),
                signal(3L, "C", "芯片产业链价格出现变化", "MARKET_MOVE")));

        assertEquals(1, clusters.size());
        assertEquals(3, clusters.get(0).getSignals().size());
    }

    @Test
    void clusteringIsIndependentFromInputOrder() {
        RadarSignal first = signal(1L, "A", "宁德时代发布新一代电池", "COMPANY");
        RadarSignal second = signal(2L, "B", "宁德时代新电池正式发布", "COMPANY");
        RadarSignal third = signal(3L, "C", "小米发布新款汽车", "COMPANY");

        List<RadarClusteringService.ClusterResult> forward = service.cluster(Arrays.asList(first, second, third));
        List<RadarSignal> reversed = Arrays.asList(first, second, third);
        Collections.reverse(reversed);
        List<RadarClusteringService.ClusterResult> backward = service.cluster(reversed);

        assertEquals(clusterSignatures(forward), clusterSignatures(backward));
    }

    @Test
    void directionConflictPreventsSameEventMerge() {
        RadarClusteringService.MatchDecision decision = service.decide(
                signal(1L, "CLS", "宁德时代电池价格上涨", "COMPANY"),
                signal(2L, "THS", "宁德时代电池价格下跌", "COMPANY"));

        assertEquals("DIFFERENT_FACT", decision.getReasonCode());
    }

    @Test
    void conflictingValuesWithTheSameUnitPreventMerge() {
        RadarSignal first = signal(1L, "CLS", "宁德时代电池产能提升至100GWh", "COMPANY");
        RadarSignal second = signal(2L, "THS", "宁德时代电池产能提升至200GWh", "COMPANY");
        RadarClusteringService.MatchDecision decision = service.decide(first, second);
        RadarEventIdentityService identities = new RadarEventIdentityService(
                new RadarTextAnalyzer(new FingerprintService()));

        assertEquals("DIFFERENT_FACT", decision.getReasonCode());
        assertNotEquals(identities.eventKey(Collections.singletonList(first)),
                identities.eventKey(Collections.singletonList(second)));
    }

    @Test
    void identicalMultiValueFactsRemainMergeable() {
        RadarClusteringService.MatchDecision decision = service.decide(
                signal(1L, "CLS", "宁德时代规划100GWh并追加200GWh电池产能", "COMPANY"),
                signal(2L, "THS", "宁德时代规划100GWh并追加200GWh电池产能", "COMPANY"));

        assertEquals("SAME", decision.getReasonCode());
    }

    @Test
    void stableIdentityIgnoresWordingAndCategoryChanges() {
        RadarTextAnalyzer analyzer = new RadarTextAnalyzer(new FingerprintService());
        RadarEventIdentityService identities = new RadarEventIdentityService(analyzer);
        RadarSignal first = signal(1L, "CLS", "宁德时代发布新一代电池", "UNCLASSIFIED");
        RadarSignal rewritten = signal(2L, "THS", "宁德时代新电池正式发布", "COMPANY");

        assertEquals(identities.eventKey(Arrays.asList(first)), identities.eventKey(Arrays.asList(rewritten)));
        assertTrue(identities.eventKey(Arrays.asList(first)).startsWith(
                analyzer.eventKey(analyzer.analyze(first)) + ":"));
    }

    @Test
    void recurringFactsOnDifferentDatesReceiveDifferentEventIdentities() {
        RadarEventIdentityService identities = new RadarEventIdentityService(
                new RadarTextAnalyzer(new FingerprintService()));
        RadarSignal first = signal(1L, "CLS", "美联储宣布维持利率不变", "MACRO_POLICY");
        RadarSignal next = signal(2L, "THS", "美联储宣布维持利率不变", "MACRO_POLICY");
        next.setPublishedAt(first.getPublishedAt().plusDays(30));

        assertNotEquals(identities.eventKey(Collections.singletonList(first)),
                identities.eventKey(Collections.singletonList(next)));
    }

    @Test
    void usesSecurityCodeAsCandidateRecallAndEventIdentity() {
        RadarClusteringService.MatchDecision decision = service.decide(
                signal(1L, "CLS", "300750 新电池发布", "COMPANY"),
                signal(2L, "THS", "300750 电池量产计划落地", "COMPANY"));

        assertEquals("SAME", decision.getReasonCode());
        assertTrue(decision.getReason().contains("标的编码"));
    }

    @Test
    void ambiguousPairIsConservativelySplitDuringClustering() {
        RadarClusteringService deterministic = new RadarClusteringService(
                new RadarTextAnalyzer(new FingerprintService()));

        List<RadarClusteringService.ClusterResult> clusters = deterministic.cluster(Arrays.asList(
                signal(1L, "CLS", "存储芯片板块持续走强", "MARKET_MOVE"),
                signal(2L, "THS", "芯片股午后集体上涨", "MARKET_MOVE")));

        assertEquals(2, clusters.size());
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

    private List<String> clusterSignatures(List<RadarClusteringService.ClusterResult> clusters) {
        List<String> values = new java.util.ArrayList<String>();
        for (RadarClusteringService.ClusterResult cluster : clusters) {
            List<Long> ids = new java.util.ArrayList<Long>();
            for (RadarSignal signal : cluster.getSignals()) ids.add(signal.getId());
            Collections.sort(ids);
            values.add(ids.toString());
        }
        Collections.sort(values);
        return values;
    }
}
