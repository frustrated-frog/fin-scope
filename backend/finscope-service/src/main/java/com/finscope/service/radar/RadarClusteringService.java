package com.finscope.service.radar;

import com.finscope.dao.radar.RadarPairDecisionRepository;
import com.finscope.domain.radar.RadarEventStatus;
import com.finscope.domain.radar.RadarPairDecision;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarSignal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class RadarClusteringService {
    private static final double SAME_THRESHOLD = 0.78;
    private static final double DIFFERENT_THRESHOLD = 0.50;
    private static final int MAX_AGENT_PAIR_CALLS = 24;
    private static final long CANDIDATE_TIME_WINDOW_HOURS = 36;
    private final RadarTextAnalyzer analyzer;
    private final RadarPairDecisionRepository decisions;
    private final RadarPairDecisionScheduler scheduler;

    public RadarClusteringService(RadarTextAnalyzer analyzer) { this(analyzer, null, null); }

    @Autowired
    public RadarClusteringService(RadarTextAnalyzer analyzer,
                                  RadarPairDecisionRepository decisions,
                                  RadarPairDecisionScheduler scheduler) {
        this.analyzer = analyzer;
        this.decisions = decisions;
        this.scheduler = scheduler;
    }

    public MatchDecision decide(RadarSignal left, RadarSignal right) {
        RadarTextAnalyzer.SignalFeatures a = analyzer.analyze(left);
        RadarTextAnalyzer.SignalFeatures b = analyzer.analyze(right);
        if (analyzer.normalize(left.getTitle()).equals(analyzer.normalize(right.getTitle()))) {
            return new MatchDecision("SAME", 1.0, "标题规范化结果一致");
        }
        if (analyzer.hasSubjectConflict(a, b)) {
            return new MatchDecision("DIFFERENT_SUBJECT", 0.0, "主要主体不同");
        }
        double score = analyzer.similarity(a, b);
        if (!a.getEntities().isEmpty() && !Collections.disjoint(a.getEntities(), b.getEntities())
                && a.getCategory().equals(b.getCategory())
                && (!a.getVariables().isEmpty() && !Collections.disjoint(a.getVariables(), b.getVariables())
                || !a.getActions().isEmpty() && !Collections.disjoint(a.getActions(), b.getActions()))) {
            return new MatchDecision("SAME", Math.max(0.83D, score), "标的编码与事实维度一致");
        }
        if (score >= SAME_THRESHOLD) return new MatchDecision("SAME", score, "主体、动作和标题语义一致");
        if (a.getCategory().equals(b.getCategory())
                && !a.getVariables().isEmpty()
                && !Collections.disjoint(a.getVariables(), b.getVariables())) {
            return new MatchDecision("AMBIGUOUS", score, "主题相近但缺少明确主体，保守拆分");
        }
        if (score < DIFFERENT_THRESHOLD) return new MatchDecision("DIFFERENT", score, "共同信息不足");
        return new MatchDecision("AMBIGUOUS", score, "相似度处于灰区，保守拆分");
    }

    public List<ClusterResult> cluster(List<RadarSignal> input) {
        List<RadarSignal> ordered = new ArrayList<RadarSignal>(input == null
                ? Collections.<RadarSignal>emptyList() : input);
        ordered.sort(Comparator.comparing(RadarSignal::getPublishedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(RadarSignal::getId));
        int[] agentCalls = new int[] { 0 };
        List<ClusterResult> clusters = new ArrayList<ClusterResult>();
        for (RadarSignal signal : ordered) {
            boolean added = false;
            for (ClusterResult cluster : clusters) {
                if (!isCandidate(cluster.representative, signal)) continue;
                MatchDecision decision = resolve(cluster.representative, signal, agentCalls);
                if (decision.isSame()) {
                    cluster.add(signal, decision);
                    added = true;
                    break;
                }
            }
            if (!added) clusters.add(newCluster(signal));
        }
        for (ClusterResult cluster : clusters) {
            cluster.finish(analyzer);
        }
        return clusters;
    }

    private boolean isCandidate(RadarSignal left, RadarSignal right) {
        if (left == null || right == null) return false;
        LocalDateTime leftTime = eventTime(left);
        LocalDateTime rightTime = eventTime(right);
        if (leftTime != null && rightTime != null
                && Math.abs(Duration.between(leftTime, rightTime).toHours()) > CANDIDATE_TIME_WINDOW_HOURS) {
            return false;
        }
        RadarTextAnalyzer.SignalFeatures a = analyzer.analyze(left);
        RadarTextAnalyzer.SignalFeatures b = analyzer.analyze(right);
        if (a.getNormalizedTitle().equals(b.getNormalizedTitle())) return true;
        if (!Collections.disjoint(a.getEntities(), b.getEntities())) return true;
        if (!Collections.disjoint(a.getSubjects(), b.getSubjects())) return true;
        return a.getCategory().equals(b.getCategory())
                && (!Collections.disjoint(a.getVariables(), b.getVariables())
                || !Collections.disjoint(a.getActions(), b.getActions()));
    }

    private LocalDateTime eventTime(RadarSignal signal) {
        return signal.getPublishedAt() == null ? signal.getFirstSeenAt() : signal.getPublishedAt();
    }

    private MatchDecision resolve(RadarSignal left, RadarSignal right, int[] agentCalls) {
        MatchDecision rule = decide(left, right);
        if (!"AMBIGUOUS".equals(rule.reasonCode) || decisions == null) return rule;
        String leftFingerprint = semanticFingerprint(left);
        String rightFingerprint = semanticFingerprint(right);
        String pairKey = RadarPairDecision.pairKey(leftFingerprint, rightFingerprint);
        try {
            Optional<RadarPairDecision> cached = decisions.find(pairKey);
            if (cached.isPresent()) {
                RadarPairDecision value = cached.get();
                return new MatchDecision(value.isSameEvent() ? "SAME_CACHE" : "DIFFERENT_CACHE",
                        value.getConfidence(), "缓存判定：" + value.getReason());
            }
        } catch (RuntimeException ignored) {
            // 缓存不可用不能阻断雷达刷新。
        }
        if (agentCalls[0] >= MAX_AGENT_PAIR_CALLS) {
            return new MatchDecision("AMBIGUOUS", rule.score, "灰区判断达到本轮预算，保守拆分");
        }
        agentCalls[0]++;
        if (scheduler != null) scheduler.schedule(left, right, leftFingerprint, rightFingerprint);
        return new MatchDecision("AMBIGUOUS", rule.score, "灰区判断已转入后台，本轮保守拆分");
    }

    private String semanticFingerprint(RadarSignal signal) {
        String value = analyzer.normalize(signal == null ? null : signal.getCategoryCode()) + "|"
                + analyzer.normalize(signal == null ? null : signal.getTitle()) + "|"
                + analyzer.normalize(signal == null ? null : signal.getContent());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception error) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private ClusterResult newCluster(RadarSignal signal) {
        ClusterResult result = new ClusterResult(signal);
        RadarEventSignal link = new RadarEventSignal();
        link.setSignalId(signal.getId()); link.setRelationType("PRIMARY"); link.setMatchScore(1.0); link.setMatchReason("代表信号");
        result.links.add(link);
        return result;
    }

    public static final class MatchDecision {
        private final String reasonCode;
        private final double score;
        private final String reason;
        MatchDecision(String reasonCode, double score, String reason) { this.reasonCode=reasonCode; this.score=score; this.reason=reason; }
        public String getReasonCode() { return reasonCode; }
        public double getScore() { return score; }
        public String getReason() { return reason; }
        boolean isSame() { return reasonCode != null && reasonCode.startsWith("SAME"); }
    }

    public static final class ClusterResult {
        private final RadarSignal representative;
        private final List<RadarSignal> signals = new ArrayList<RadarSignal>();
        private final List<RadarEventSignal> links = new ArrayList<RadarEventSignal>();
        private final RadarEvent event = new RadarEvent();

        ClusterResult(RadarSignal representative) { this.representative = representative; signals.add(representative); }
        void add(RadarSignal signal, MatchDecision decision) {
            signals.add(signal); RadarEventSignal link = new RadarEventSignal(); link.setSignalId(signal.getId());
            link.setRelationType("SUPPORTING"); link.setMatchScore(decision.score); link.setMatchReason(decision.reason); links.add(link);
        }
        void finish(RadarTextAnalyzer analyzer) {
            RadarTextAnalyzer.SignalFeatures features = analyzer.analyze(representative);
            event.setEventKey(analyzer.eventKey(features)); event.setCanonicalTitle(representative.getTitle());
            event.setSummary(firstNonBlank(representative.getContent(), representative.getTitle()));
            event.setCategoryCode(features.getCategory()); event.setStatus(RadarEventStatus.ACTIVE.code());
            LocalDateTime first = null, last = null; Set<String> providers = new HashSet<String>();
            for (RadarSignal signal : signals) { LocalDateTime time = signal.getPublishedAt() == null ? signal.getFirstSeenAt() : signal.getPublishedAt();
                if (time != null && (first == null || time.isBefore(first))) first=time; if (time != null && (last == null || time.isAfter(last))) last=time;
                providers.add(firstNonBlank(signal.getProviderCode(), signal.getSourceName())); }
            event.setFirstSeenAt(first); event.setLastSeenAt(last); event.setUpdatedAt(last);
            event.setSourceCount(providers.size()); event.setSignalCount(signals.size());
        }
        private String firstNonBlank(String first, String second) { return first == null || first.trim().isEmpty() ? second : first; }
        public RadarEvent getEvent() { return event; }
        public List<RadarSignal> getSignals() { return signals; }
        public List<RadarEventSignal> getLinks() { return links; }
    }
}
