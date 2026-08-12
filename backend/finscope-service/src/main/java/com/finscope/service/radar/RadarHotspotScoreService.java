package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEventSnapshot;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.dedupe.FingerprintService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class RadarHotspotScoreService {
    public static final String SCORE_VERSION = "HOTSPOT_V2";
    private final RadarSourceIndependenceService independence;
    private final RadarLifecycleService lifecycles;

    public RadarHotspotScoreService() {
        this(defaultIndependence(), new RadarLifecycleService());
    }

    @Autowired
    public RadarHotspotScoreService(RadarSourceIndependenceService independence,
                                    RadarLifecycleService lifecycles) {
        this.independence = independence;
        this.lifecycles = lifecycles;
    }

    private static RadarSourceIndependenceService defaultIndependence() {
        return new RadarSourceIndependenceService(new RadarTextAnalyzer(new FingerprintService()));
    }

    public Score score(List<RadarSignal> signals, LocalDateTime now) {
        return score(signals, now, null);
    }

    public Score score(List<RadarSignal> signals, LocalDateTime now, RadarEventSnapshot previous) {
        List<RadarSignal> values = signals == null ? Collections.<RadarSignal>emptyList() : signals;
        if (values.isEmpty()) return Score.empty();
        RadarSourceIndependenceService.Analysis sources = independence.analyze(values);
        int independentCount = sources.getIndependentSourceCount();
        double freshness = freshness(values, now);
        double burst = burst(independentCount, previous, freshness, now);
        double confirmation = confirmation(independentCount);
        double authority = sources.getAuthorityScore();
        double rankTrend = rankTrend(values);
        double persistence = persistence(independentCount, previous);
        double weighted = burst * 0.30D + confirmation * 0.22D + freshness * 0.18D
                + authority * 0.15D + rankTrend * 0.10D + persistence * 0.05D;
        int total = bounded(weighted * 100.0D);
        int confidence = confidence(sources, values);
        LocalDateTime latest = latestTime(values);
        String lifecycle = lifecycles.next(normalizedPrevious(previous, independentCount), total,
                independentCount, latest, now);
        String explanation = "传播速度/爆发强度 " + percentage(burst)
                + "；" + (independentCount > 1 ? "多源独立确认 " : "独立确认 ") + percentage(confirmation)
                + "；时效 " + percentage(freshness)
                + "；来源权威 " + percentage(authority)
                + "；排名趋势 " + percentage(rankTrend)
                + "；持续性 " + percentage(persistence)
                + "；可信度 " + confidence + "%"
                + "；生命周期 " + lifecycle
                + "；市场反应/用户互动未接入";
        return new Score(total, confidence, explanation, burst, confirmation, authority,
                freshness, rankTrend, persistence, independentCount, lifecycle);
    }

    private RadarEventSnapshot normalizedPrevious(RadarEventSnapshot previous, int independentCount) {
        if (previous == null || previous.getIndependentSourceCount() > 0) return previous;
        RadarEventSnapshot normalized = new RadarEventSnapshot();
        normalized.setSnapshotAt(previous.getSnapshotAt());
        normalized.setSignalCount(previous.getSignalCount());
        normalized.setIndependentSourceCount(Math.min(previous.getSignalCount(), independentCount));
        normalized.setHotnessScore(previous.getHotnessScore());
        normalized.setLifecycleState(previous.getLifecycleState());
        return normalized;
    }

    private double burst(int independentCount, RadarEventSnapshot previous, double freshness, LocalDateTime now) {
        if (previous == null || previous.getSnapshotAt() == null || now == null) {
            return clamp(independentCount / 2.0D) * freshness;
        }
        int previousCount = previous.getIndependentSourceCount() > 0
                ? previous.getIndependentSourceCount() : Math.min(previous.getSignalCount(), independentCount);
        int added = Math.max(0, independentCount - previousCount);
        long minutes = Math.max(1, Duration.between(previous.getSnapshotAt(), now).toMinutes());
        double normalizedWindow = Math.max(1.0D, minutes / 30.0D);
        return clamp(added / normalizedWindow / 2.0D);
    }

    private double confirmation(int independentCount) {
        if (independentCount <= 0) return 0;
        if (independentCount == 1) return 0.25D;
        return clamp(0.25D + Math.log(independentCount) / Math.log(3.0D) * 0.75D);
    }

    private double freshness(List<RadarSignal> signals, LocalDateTime now) {
        double total = 0;
        for (RadarSignal signal : signals) {
            LocalDateTime at = eventTime(signal);
            if (at == null || now == null) continue;
            double ageHours = Math.max(0, Duration.between(at, now).toMinutes()) / 60.0D;
            if (ageHours >= 48.0D) continue;
            total += Math.pow(0.5D, ageHours / halfLifeHours(signal.getCategoryCode()));
        }
        return total / signals.size();
    }

    private double halfLifeHours(String category) {
        if ("MARKET_MOVE".equalsIgnoreCase(category)) return 2.0D;
        if ("MACRO_POLICY".equalsIgnoreCase(category)) return 12.0D;
        if ("RESEARCH".equalsIgnoreCase(category)) return 24.0D;
        if ("COMPANY".equalsIgnoreCase(category)) return 8.0D;
        return 4.0D;
    }

    private double rankTrend(List<RadarSignal> signals) {
        double total = 0;
        for (RadarSignal signal : signals) {
            Integer current = signal.getSourceRank();
            Integer previous = signal.getPreviousSourceRank();
            if (current == null) {
                total += 0.35D;
            } else if (previous == null) {
                total += clamp(1.0D - (Math.max(1, current) - 1) / 20.0D);
            } else {
                total += clamp(0.5D + (previous - current) / 20.0D);
            }
        }
        return total / signals.size();
    }

    private double persistence(int independentCount, RadarEventSnapshot previous) {
        if (previous == null) return independentCount > 1 ? 0.45D : 0.25D;
        int before = previous.getIndependentSourceCount() > 0
                ? previous.getIndependentSourceCount() : previous.getSignalCount();
        if (independentCount > before) return 1.0D;
        if (independentCount == before) return 0.65D;
        return 0.25D;
    }

    private int confidence(RadarSourceIndependenceService.Analysis sources, List<RadarSignal> signals) {
        double official = sources.hasOfficialSource() ? 0.30D : 0;
        double independent = confirmation(sources.getIndependentSourceCount()) * 0.30D;
        double authority = sources.getAuthorityScore() * 0.25D;
        double completeness = factCompleteness(signals) * 0.15D;
        double repostPenalty = sources.getRepostConcentration() * 0.20D;
        return bounded((official + independent + authority + completeness - repostPenalty) * 100.0D);
    }

    private double factCompleteness(List<RadarSignal> signals) {
        int complete = 0;
        for (RadarSourceIndependenceService.Observation observation : independence.analyze(signals).getObservations()) {
            RadarSignalFeatures value = observation.getFeatures();
            if ((!value.getSubjects().isEmpty() || !value.getEntities().isEmpty())
                    && (!value.getActions().isEmpty() || !value.getVariables().isEmpty())) complete++;
        }
        return signals.isEmpty() ? 0 : complete / (double) signals.size();
    }

    private LocalDateTime latestTime(List<RadarSignal> signals) {
        LocalDateTime latest = null;
        for (RadarSignal signal : signals) {
            LocalDateTime at = eventTime(signal);
            if (at != null && (latest == null || at.isAfter(latest))) latest = at;
        }
        return latest;
    }

    private LocalDateTime eventTime(RadarSignal signal) {
        return signal.getPublishedAt() == null ? signal.getFirstSeenAt() : signal.getPublishedAt();
    }

    private String percentage(double value) { return bounded(value * 100.0D) + "%"; }
    private int bounded(double value) { return Math.max(0, Math.min(100, (int) Math.round(value))); }
    private double clamp(double value) { return Math.max(0, Math.min(1, value)); }

    public static final class Score {
        private final int totalScore;
        private final int confidenceScore;
        private final String explanation;
        private final double burstScore;
        private final double confirmationScore;
        private final double authorityScore;
        private final double freshnessScore;
        private final double rankTrendScore;
        private final double persistenceScore;
        private final int independentSourceCount;
        private final String lifecycleState;

        Score(int totalScore, int confidenceScore, String explanation, double burstScore,
              double confirmationScore, double authorityScore, double freshnessScore,
              double rankTrendScore, double persistenceScore, int independentSourceCount,
              String lifecycleState) {
            this.totalScore = totalScore;
            this.confidenceScore = confidenceScore;
            this.explanation = explanation;
            this.burstScore = burstScore;
            this.confirmationScore = confirmationScore;
            this.authorityScore = authorityScore;
            this.freshnessScore = freshnessScore;
            this.rankTrendScore = rankTrendScore;
            this.persistenceScore = persistenceScore;
            this.independentSourceCount = independentSourceCount;
            this.lifecycleState = lifecycleState;
        }

        static Score empty() {
            return new Score(0, 0, "暂无可评分信号", 0, 0, 0, 0, 0, 0, 0, "QUIET");
        }

        public int getTotalScore() { return totalScore; }
        public int getConfidenceScore() { return confidenceScore; }
        public String getExplanation() { return explanation; }
        public double getVelocityScore() { return burstScore; }
        public double getBurstScore() { return burstScore; }
        public double getSourceBreadthScore() { return confirmationScore; }
        public double getConfirmationScore() { return confirmationScore; }
        public double getSourceAuthorityScore() { return authorityScore; }
        public double getNoveltyScore() { return freshnessScore; }
        public double getCrossPlatformSpreadScore() { return confirmationScore; }
        public double getRankTrendScore() { return rankTrendScore; }
        public double getPersistenceScore() { return persistenceScore; }
        public int getIndependentSourceCount() { return independentSourceCount; }
        public String getLifecycleState() { return lifecycleState; }

        public RadarEventSnapshot toSnapshot(Long eventId, int signalCount, int sourceCount, LocalDateTime at) {
            RadarEventSnapshot snapshot = new RadarEventSnapshot();
            snapshot.setEventId(eventId);
            snapshot.setSnapshotAt(at);
            snapshot.setSignalCount(signalCount);
            snapshot.setIndependentSourceCount(independentSourceCount);
            snapshot.setVelocityScore(burstScore);
            snapshot.setHotnessScore(totalScore);
            snapshot.setConfirmationScore(confirmationScore);
            snapshot.setFreshnessScore(freshnessScore);
            snapshot.setRankTrendScore(rankTrendScore);
            snapshot.setConfidenceScore(confidenceScore);
            snapshot.setScoreVersion(SCORE_VERSION);
            snapshot.setLifecycleState(lifecycleState);
            snapshot.setExplanation(explanation);
            return snapshot;
        }
    }
}
