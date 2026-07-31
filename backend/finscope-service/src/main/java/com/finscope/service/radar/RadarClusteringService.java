package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarSignal;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class RadarClusteringService {
    private static final double SAME_THRESHOLD = 0.78;
    private static final double DIFFERENT_THRESHOLD = 0.50;
    private final RadarTextAnalyzer analyzer;

    public RadarClusteringService(RadarTextAnalyzer analyzer) { this.analyzer = analyzer; }

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
        List<ClusterResult> clusters = new ArrayList<ClusterResult>();
        for (RadarSignal signal : ordered) {
            ClusterResult target = null;
            MatchDecision accepted = null;
            for (ClusterResult candidate : clusters) {
                MatchDecision decision = decide(candidate.representative, signal);
                if ("SAME".equals(decision.reasonCode)) { target = candidate; accepted = decision; break; }
            }
            if (target == null) clusters.add(newCluster(signal));
            else target.add(signal, accepted);
        }
        for (ClusterResult cluster : clusters) cluster.finish(analyzer);
        return clusters;
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
