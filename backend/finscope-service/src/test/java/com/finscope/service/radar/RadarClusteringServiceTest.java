package com.finscope.service.radar;

import com.finscope.dao.radar.RadarPairDecisionRepository;
import com.finscope.domain.radar.RadarPairDecision;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.dedupe.FingerprintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void cachedPairDecisionAvoidsRepeatedAgentCall() {
        RadarPairDecisionRepository decisions = mock(RadarPairDecisionRepository.class);
        RadarPairDecisionScheduler scheduler = mock(RadarPairDecisionScheduler.class);
        RadarPairDecision cached = new RadarPairDecision();
        cached.setSameEvent(true);
        cached.setConfidence(0.88D);
        cached.setReason("历史灰区判断确认同一事件");
        cached.setDecisionSource("AGENT");
        when(decisions.find(any())).thenReturn(Optional.of(cached));
        RadarClusteringService cachedClustering = new RadarClusteringService(
                new RadarTextAnalyzer(new FingerprintService()), decisions, scheduler);

        List<RadarClusteringService.ClusterResult> clusters = cachedClustering.cluster(Arrays.asList(
                signal(1L, "CLS", "存储芯片板块持续走强", "MARKET_MOVE"),
                signal(2L, "THS", "芯片股午后集体上涨", "MARKET_MOVE")));

        assertEquals(1, clusters.size());
        assertEquals("缓存判定：历史灰区判断确认同一事件", clusters.get(0).getLinks().get(1).getMatchReason());
        verify(scheduler, org.mockito.Mockito.never()).schedule(any(), any(), any(), any());
    }

    @Test
    void graphClusteringKeepsIndirectlyConnectedSignalsTogether() {
        RadarPairDecisionRepository decisions = mock(RadarPairDecisionRepository.class);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        when(decisions.find(any())).thenAnswer(invocation -> {
            int call = calls.incrementAndGet();
            RadarPairDecision cached = new RadarPairDecision();
            cached.setSameEvent(call == 1 || call == 3);
            cached.setConfidence(0.86D);
            cached.setReason(cached.isSameEvent() ? "相邻报道确认同一事件" : "关键事实不同");
            return Optional.of(cached);
        });
        RadarClusteringService graphClustering = new RadarClusteringService(
                new RadarTextAnalyzer(new FingerprintService()), decisions, mock(RadarPairDecisionScheduler.class));

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
    void stableIdentityIgnoresWordingAndCategoryChanges() {
        RadarEventIdentityService identities = new RadarEventIdentityService(new RadarTextAnalyzer(new FingerprintService()));
        RadarSignal first = signal(1L, "CLS", "宁德时代发布新一代电池", "UNCLASSIFIED");
        RadarSignal rewritten = signal(2L, "THS", "宁德时代新电池正式发布", "COMPANY");

        assertEquals(identities.eventKey(Arrays.asList(first)), identities.eventKey(Arrays.asList(rewritten)));
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
    void uncachedAmbiguousPairIsScheduledAndConservativelySplitForCurrentRefresh() {
        RadarPairDecisionRepository decisions=mock(RadarPairDecisionRepository.class);
        RadarPairDecisionScheduler scheduler=mock(RadarPairDecisionScheduler.class);
        when(decisions.find(any())).thenReturn(Optional.empty());
        RadarClusteringService asynchronous=new RadarClusteringService(
                new RadarTextAnalyzer(new FingerprintService()),decisions,scheduler);

        List<RadarClusteringService.ClusterResult> clusters=asynchronous.cluster(Arrays.asList(
                signal(1L,"CLS","存储芯片板块持续走强","MARKET_MOVE"),
                signal(2L,"THS","芯片股午后集体上涨","MARKET_MOVE")));

        assertEquals(2,clusters.size());
        verify(scheduler).schedule(any(),any(),any(),any());
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
