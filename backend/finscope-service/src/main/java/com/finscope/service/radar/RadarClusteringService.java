package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEventStatus;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarSignal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

@Service
public class RadarClusteringService {
    private static final double SAME_THRESHOLD = 0.78;
    private static final double DIFFERENT_THRESHOLD = 0.50;
    private static final long CANDIDATE_TIME_WINDOW_HOURS = 36;
    private final RadarTextAnalyzer analyzer;
    private final RadarEventIdentityService identities;

    public RadarClusteringService(RadarTextAnalyzer analyzer) {
        this(analyzer, new RadarEventIdentityService(analyzer));
    }

    @Autowired
    public RadarClusteringService(RadarTextAnalyzer analyzer,
                                  RadarEventIdentityService identities) {
        this.analyzer = analyzer;
        this.identities = identities;
    }

    public MatchDecision decide(RadarSignal left, RadarSignal right) {
        RadarTextAnalyzer.SignalFeatures a = analyzer.analyze(left);
        RadarTextAnalyzer.SignalFeatures b = analyzer.analyze(right);
        if (analyzer.hasSubjectConflict(a, b)) {
            return new MatchDecision("DIFFERENT_SUBJECT", 0.0, "主要主体不同");
        }
        RadarSignalFeatures leftFeatures = analyzer.extract(left);
        RadarSignalFeatures rightFeatures = analyzer.extract(right);
        if (analyzer.hasFactConflict(leftFeatures, rightFeatures)) {
            return new MatchDecision("DIFFERENT_FACT", 0.0, "主体、方向或关键动作冲突");
        }
        if (!leftFeatures.getNormalizedContent().isEmpty()
                && leftFeatures.getNormalizedContent().equals(rightFeatures.getNormalizedContent())) {
            return new MatchDecision("SAME", 1.0, "正文规范化结果一致");
        }
        if (analyzer.normalize(left.getTitle()).equals(analyzer.normalize(right.getTitle()))) {
            return new MatchDecision("SAME", 1.0, "标题规范化结果一致");
        }
        double score = analyzer.similarity(a, b);
        if (!a.getEntities().isEmpty() && !Collections.disjoint(a.getEntities(), b.getEntities())
                && a.getCategory().equals(b.getCategory())
                && (!a.getVariables().isEmpty() && !Collections.disjoint(a.getVariables(), b.getVariables())
                || !a.getActions().isEmpty() && !Collections.disjoint(a.getActions(), b.getActions()))) {
            return new MatchDecision("SAME", Math.max(0.83D, score), "标的编码与事实维度一致");
        }
        if (score >= SAME_THRESHOLD) {
            return new MatchDecision("SAME", score, "主体、动作和标题语义一致");
        }
        if (a.getCategory().equals(b.getCategory())
                && !a.getVariables().isEmpty()
                && !Collections.disjoint(a.getVariables(), b.getVariables())) {
            return new MatchDecision("AMBIGUOUS", score, "主题相近但缺少明确主体，保守拆分");
        }
        if (score < DIFFERENT_THRESHOLD) {
            return new MatchDecision("DIFFERENT", score, "共同信息不足");
        }
        return new MatchDecision("AMBIGUOUS", score, "相似度处于灰区，保守拆分");
    }

    public List<ClusterResult> cluster(List<RadarSignal> input) {
        List<RadarSignal> ordered = new ArrayList<RadarSignal>(input == null
                ? Collections.<RadarSignal>emptyList() : input);
        ordered.sort(Comparator.comparing(RadarSignal::getPublishedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(RadarSignal::getId));
        Map<RadarSignal, RadarSignalFeatures> features = new IdentityHashMap<RadarSignal, RadarSignalFeatures>();
        for (RadarSignal signal : ordered) {
            features.put(signal, analyzer.extract(signal));
        }
        int[] parents = new int[ordered.size()];
        for (int index = 0; index < parents.length; index++) {
            parents[index] = index;
        }
        Map<String, MatchDecision> edges = new LinkedHashMap<String, MatchDecision>();
        for (int left = 0; left < ordered.size(); left++) {
            for (int right = left + 1; right < ordered.size(); right++) {
                if (!isCandidate(features.get(ordered.get(left)), features.get(ordered.get(right)))) {
                    continue;
                }
                MatchDecision decision = decide(ordered.get(left), ordered.get(right));
                edges.put(edgeKey(left, right), decision);
                if (decision.isSame() && componentsCompatible(parents, ordered, features, left, right)) {
                    union(parents, left, right);
                }
            }
        }
        Map<Integer, List<Integer>> components = new LinkedHashMap<Integer, List<Integer>>();
        for (int index = 0; index < ordered.size(); index++) {
            int root = find(parents, index);
            components.computeIfAbsent(root, ignored -> new ArrayList<Integer>()).add(index);
        }
        List<ClusterResult> clusters = new ArrayList<ClusterResult>();
        for (List<Integer> component : components.values()) {
            ClusterResult cluster = newCluster(ordered.get(component.get(0)));
            for (int position = 1; position < component.size(); position++) {
                int index = component.get(position);
                MatchDecision decision = supportingDecision(component, position, edges);
                cluster.add(ordered.get(index), decision == null
                        ? new MatchDecision("SAME_GRAPH", 0.78D, "事件图传递关联") : decision);
            }
            cluster.finish(analyzer, identities);
            clusters.add(cluster);
        }
        return clusters;
    }

    private boolean isCandidate(RadarSignalFeatures a, RadarSignalFeatures b) {
        if (a == null || b == null) {
            return false;
        }
        LocalDateTime leftTime = a.getEventTime();
        LocalDateTime rightTime = b.getEventTime();
        if (leftTime != null && rightTime != null
                && Math.abs(Duration.between(leftTime, rightTime).toHours()) > CANDIDATE_TIME_WINDOW_HOURS) {
            return false;
        }
        if (a.getNormalizedTitle().equals(b.getNormalizedTitle())) {
            return true;
        }
        if (!Collections.disjoint(a.getEntities(), b.getEntities())) {
            return true;
        }
        if (!Collections.disjoint(a.getSubjects(), b.getSubjects())) {
            return true;
        }
        return a.getCategory().equals(b.getCategory())
                && (!Collections.disjoint(a.getVariables(), b.getVariables())
                || !Collections.disjoint(a.getActions(), b.getActions()));
    }

    private boolean componentsCompatible(int[] parents, List<RadarSignal> signals,
                                         Map<RadarSignal, RadarSignalFeatures> features, int left, int right) {
        int leftRoot = find(parents, left);
        int rightRoot = find(parents, right);
        if (leftRoot == rightRoot) {
            return true;
        }
        for (int first = 0; first < signals.size(); first++) {
            if (find(parents, first) != leftRoot) {
                continue;
            }
            for (int second = 0; second < signals.size(); second++) {
                if (find(parents, second) == rightRoot
                        && analyzer.hasFactConflict(features.get(signals.get(first)), features.get(signals.get(second)))) {
                    return false;
                }
            }
        }
        return true;
    }

    private MatchDecision supportingDecision(List<Integer> component, int position,
                                             Map<String, MatchDecision> edges) {
        int current = component.get(position);
        for (int prior = 0; prior < position; prior++) {
            MatchDecision decision = edges.get(edgeKey(component.get(prior), current));
            if (decision != null && decision.isSame()) {
                return decision;
            }
        }
        return null;
    }

    private String edgeKey(int left, int right) {
        return Math.min(left, right) + ":" + Math.max(left, right);
    }

    private int find(int[] parents, int value) {
        if (parents[value] != value) {
            parents[value] = find(parents, parents[value]);
        }
        return parents[value];
    }

    private void union(int[] parents, int left, int right) {
        int leftRoot = find(parents, left);
        int rightRoot = find(parents, right);
        if (leftRoot != rightRoot) {
            parents[rightRoot] = leftRoot;
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

        ClusterResult(RadarSignal representative) {
            this.representative = representative;
            signals.add(representative);
        }

        void add(RadarSignal signal, MatchDecision decision) {
            signals.add(signal);
            RadarEventSignal link = new RadarEventSignal();
            link.setSignalId(signal.getId());
            link.setRelationType("SUPPORTING");
            link.setMatchScore(decision.score);
            link.setMatchReason(decision.reason);
            links.add(link);
        }
        void finish(RadarTextAnalyzer analyzer, RadarEventIdentityService identities) {
            RadarTextAnalyzer.SignalFeatures features = analyzer.analyze(representative);
            event.setEventKey(identities.eventKey(signals)); event.setCanonicalTitle(representative.getTitle());
            event.setSummary(firstNonBlank(representative.getContent(), representative.getTitle()));
            event.setCategoryCode(features.getCategory()); event.setStatus(RadarEventStatus.ACTIVE.code());
            LocalDateTime first = null;
            LocalDateTime last = null;
            Set<String> providers = new HashSet<String>();
            for (RadarSignal signal : signals) {
                LocalDateTime time = signal.getPublishedAt() == null
                        ? signal.getFirstSeenAt() : signal.getPublishedAt();
                if (time != null && (first == null || time.isBefore(first))) {
                    first = time;
                }
                if (time != null && (last == null || time.isAfter(last))) {
                    last = time;
                }
                providers.add(firstNonBlank(signal.getProviderCode(), signal.getSourceName()));
            }
            event.setFirstSeenAt(first); event.setLastSeenAt(last); event.setUpdatedAt(last);
            event.setSourceCount(providers.size()); event.setSignalCount(signals.size());
        }
        private String firstNonBlank(String first, String second) { return first == null || first.trim().isEmpty() ? second : first; }
        public RadarEvent getEvent() { return event; }
        public List<RadarSignal> getSignals() { return signals; }
        public List<RadarEventSignal> getLinks() { return links; }
    }
}
