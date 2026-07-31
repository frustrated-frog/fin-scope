package com.finscope.service.radar;

import com.finscope.dao.radar.RadarPairDecisionRepository;
import com.finscope.domain.radar.RadarPairDecision;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarSignal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

@Service
public class RadarClusteringService {
    private static final double SAME_THRESHOLD = 0.78;
    private static final double DIFFERENT_THRESHOLD = 0.50;
    private static final int MAX_AGENT_PAIR_CALLS = 24;
    private final RadarTextAnalyzer analyzer;
    private final RadarPairDecisionRepository decisions;
    private final RadarEventMatchAgent matchAgent;
    private final RadarCanonicalTitleAgent titleAgent;

    public RadarClusteringService(RadarTextAnalyzer analyzer) { this(analyzer, null, null, null); }

    public RadarClusteringService(RadarTextAnalyzer analyzer,
                                  RadarPairDecisionRepository decisions,
                                  RadarEventMatchAgent matchAgent) {
        this(analyzer, decisions, matchAgent, null);
    }

    @Autowired
    public RadarClusteringService(RadarTextAnalyzer analyzer,
                                  RadarPairDecisionRepository decisions,
                                  RadarEventMatchAgent matchAgent,
                                  RadarCanonicalTitleAgent titleAgent) {
        this.analyzer = analyzer;
        this.decisions = decisions;
        this.matchAgent = matchAgent;
        this.titleAgent = titleAgent;
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
        List<RadarSignal> ordered = new ArrayList<RadarSignal>(input);
        ordered.sort(Comparator.comparing(RadarSignal::getPublishedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(RadarSignal::getId));
        Map<Integer, Set<Integer>> adjacency = new HashMap<Integer, Set<Integer>>();
        Map<String, MatchDecision> accepted = new HashMap<String, MatchDecision>();
        int[] agentCalls = new int[] { 0 };
        for (int index = 0; index < ordered.size(); index++) adjacency.put(index, new HashSet<Integer>());
        for (int left = 0; left < ordered.size(); left++) {
            for (int right = left + 1; right < ordered.size(); right++) {
                MatchDecision decision = resolve(ordered.get(left), ordered.get(right), agentCalls);
                if (decision.isSame()) {
                    adjacency.get(left).add(right);
                    adjacency.get(right).add(left);
                    accepted.put(edgeKey(left, right), decision);
                }
            }
        }
        List<ClusterResult> clusters = new ArrayList<ClusterResult>();
        Set<Integer> visited = new HashSet<Integer>();
        for (int start = 0; start < ordered.size(); start++) {
            if (visited.contains(start)) continue;
            List<Integer> component = new ArrayList<Integer>();
            Queue<Integer> queue = new LinkedList<Integer>();
            queue.add(start);
            visited.add(start);
            while (!queue.isEmpty()) {
                int current = queue.remove();
                component.add(current);
                for (Integer neighbor : adjacency.get(current)) {
                    if (visited.add(neighbor)) queue.add(neighbor);
                }
            }
            component.sort(Integer::compareTo);
            ClusterResult cluster = newCluster(ordered.get(component.get(0)));
            for (int position = 1; position < component.size(); position++) {
                int signalIndex = component.get(position);
                MatchDecision link = firstAcceptedLink(signalIndex, component, accepted);
                cluster.add(ordered.get(signalIndex), link);
            }
            clusters.add(cluster);
        }
        for (ClusterResult cluster : clusters) {
            cluster.finish(analyzer);
            if (titleAgent != null && cluster.signals.size() > 1) {
                RadarCanonicalTitleAgent.Result title = titleAgent.generate(
                        cluster.signals, cluster.event.getCanonicalTitle());
                cluster.event.setCanonicalTitle(title.getTitle());
            }
        }
        return clusters;
    }

    private MatchDecision resolve(RadarSignal left, RadarSignal right, int[] agentCalls) {
        MatchDecision rule = decide(left, right);
        if (!"AMBIGUOUS".equals(rule.reasonCode) || decisions == null || matchAgent == null) return rule;
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
        RadarEventMatchAgent.Decision agentDecision = matchAgent.decide(left, right);
        boolean generatedByAgent = "AGENT".equals(agentDecision.getSource());
        MatchDecision resolved = new MatchDecision(agentDecision.isSameEvent() ? "SAME_AGENT" : "DIFFERENT_AGENT",
                agentDecision.getConfidence(), (generatedByAgent ? "Agent判定：" : "回退判定：")
                + agentDecision.getReason());
        if (!generatedByAgent) {
            return resolved;
        }
        try {
            RadarPairDecision stored = new RadarPairDecision();
            stored.setPairKey(pairKey);
            if (leftFingerprint.compareTo(rightFingerprint) <= 0) {
                stored.setLeftFingerprint(leftFingerprint);
                stored.setRightFingerprint(rightFingerprint);
            } else {
                stored.setLeftFingerprint(rightFingerprint);
                stored.setRightFingerprint(leftFingerprint);
            }
            stored.setSameEvent(agentDecision.isSameEvent());
            stored.setConfidence(agentDecision.getConfidence());
            stored.setReason(agentDecision.getReason());
            stored.setDecisionSource(agentDecision.getSource());
            decisions.save(stored);
        } catch (RuntimeException ignored) {
            // 判定结果仍可用于本轮聚类，缓存写入失败仅损失复用能力。
        }
        return resolved;
    }

    private MatchDecision firstAcceptedLink(int signalIndex, List<Integer> component,
                                            Map<String, MatchDecision> accepted) {
        for (Integer candidate : component) {
            if (candidate == signalIndex) continue;
            MatchDecision decision = accepted.get(edgeKey(candidate, signalIndex));
            if (decision != null) return decision;
        }
        return new MatchDecision("SAME_TRANSITIVE", 0.5D, "通过同事件关系图间接关联");
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

    private String edgeKey(int first, int second) {
        return Math.min(first, second) + ":" + Math.max(first, second);
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
            event.setCategoryCode(features.getCategory()); event.setStatus("ACTIVE");
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
