package com.finscope.service.radar;

import com.finscope.domain.radar.RadarSignal;
import com.finscope.domain.radar.RadarEventSnapshot;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RadarHotspotScoreService {
    private static final int MAX_SOURCE_RANK = 20;

    public Score score(List<RadarSignal> signals, LocalDateTime now) {
        return score(signals, now, null);
    }

    public Score score(List<RadarSignal> signals, LocalDateTime now, RadarEventSnapshot previous) {
        List<RadarSignal> values = signals == null ? Collections.<RadarSignal>emptyList() : signals;
        if (values.isEmpty()) return new Score(0, "暂无可评分信号", 0, 0, 0, 0, 0, 0, "QUIET");

        // PRD 的市场反应、用户互动在当前个人雷达中没有可靠数据源，暂不虚构，
        // 只对本地可观测的六个维度归一化后评分。
        double sourceBreadth = clamp(uniqueProviders(values) / 3.0D);
        double velocity = publishVelocity(values, previous, now);
        double authority = max(values, this::quality);
        double novelty = average(values, signal -> recency(signal, now));
        double spread = clamp(uniqueSourceNames(values) / 3.0D);
        double persistence = persistence(values, previous);
        double weighted = sourceBreadth * 0.22D + velocity * 0.20D + authority * 0.15D
                + novelty * 0.12D + spread * 0.10D + persistence * 0.08D;
        int total = bounded(round(weighted / 0.87D * 100.0D));
        String lifecycle = lifecycle(previous, total, velocity);

        List<String> reasons = new ArrayList<String>();
        reasons.add("来源广度 " + percentage(sourceBreadth));
        if (uniqueProviders(values) > 1) reasons.add("多源交叉覆盖");
        reasons.add("传播速度 " + percentage(velocity));
        reasons.add("来源权威 " + percentage(authority));
        reasons.add("新意 " + percentage(novelty));
        reasons.add("跨源扩散 " + percentage(spread));
        reasons.add("持续性 " + percentage(persistence));
        reasons.add("生命周期 " + lifecycle);
        reasons.add("市场反应/用户互动未接入");
        return new Score(total, String.join("；", reasons), velocity, sourceBreadth, authority,
                novelty, spread, persistence, lifecycle);
    }

    private double quality(RadarSignal signal) {
        if (signal == null) return 0;
        if (signal.getSourceWeight() > 0) return clamp(signal.getSourceWeight());
        return RadarSourceQuality.resolve(signal.getSourceTier()).getHotnessWeight();
    }

    private double rank(RadarSignal signal) {
        if (signal == null || signal.getSourceRank() == null) return 0.5D;
        return clamp(1.0D - (Math.max(1, signal.getSourceRank()) - 1) / (double) MAX_SOURCE_RANK);
    }

    private double recency(RadarSignal signal, LocalDateTime now) {
        if (signal == null || now == null) return 0;
        LocalDateTime published = signal.getPublishedAt() == null ? signal.getLastSeenAt() : signal.getPublishedAt();
        if (published == null) return 0;
        long minutes = Math.max(0, Duration.between(published, now).toMinutes());
        return clamp(1.0D - minutes / (24.0D * 60.0D));
    }

    private int uniqueProviders(List<RadarSignal> signals) {
        Set<String> providers = new HashSet<String>();
        for (RadarSignal signal : signals) {
            if (signal == null) continue;
            String provider = safe(signal.getProviderCode());
            if (provider.isEmpty()) provider = safe(signal.getSourceName());
            if (!provider.isEmpty()) providers.add(provider.toUpperCase(Locale.ROOT));
        }
        return providers.size();
    }

    private int uniqueSourceNames(List<RadarSignal> signals) {
        Set<String> providers = new HashSet<String>();
        for (RadarSignal signal : signals) {
            if (signal == null) continue;
            String provider = safe(signal.getSourceName());
            if (provider.isEmpty()) provider = safe(signal.getProviderCode());
            if (!provider.isEmpty()) providers.add(provider.toUpperCase(Locale.ROOT));
        }
        return providers.size();
    }

    private double publishVelocity(List<RadarSignal> signals, RadarEventSnapshot previous, LocalDateTime now) {
        if (previous == null || previous.getSnapshotAt() == null || now == null) {
            return clamp(signals.size() / 2.0D);
        }
        long minutes = Math.max(1, Duration.between(previous.getSnapshotAt(), now).toMinutes());
        int newSignals = Math.max(0, signals.size() - previous.getSignalCount());
        return clamp(newSignals / Math.max(0.25D, minutes / 30.0D) / 2.0D);
    }

    private double persistence(List<RadarSignal> signals, RadarEventSnapshot previous) {
        if (previous == null) return signals.size() > 1 ? 0.40D : 0.25D;
        if (signals.size() > previous.getSignalCount()) return 1.0D;
        if (signals.size() == previous.getSignalCount()) return 0.65D;
        return 0.25D;
    }

    private String lifecycle(RadarEventSnapshot previous, int total, double velocity) {
        if (previous == null) return "DISCOVERED";
        if (velocity >= 0.70D) return "RISING";
        if (velocity <= 0.20D && previous.getHotnessScore() > total) return "COOLING";
        if (velocity <= 0.20D) return "QUIET";
        return "STABLE";
    }

    private double max(List<RadarSignal> signals, Value value) {
        double result = 0;
        for (RadarSignal signal : signals) result = Math.max(result, value.get(signal));
        return result;
    }

    private String percentage(double value) { return round(value * 100.0D) + "%"; }

    private double average(List<RadarSignal> signals, Value value) {
        double total = 0;
        for (RadarSignal signal : signals) total += value.get(signal);
        return total / signals.size();
    }

    private int round(double value) { return (int) Math.round(value); }
    private int bounded(int value) { return Math.max(0, Math.min(100, value)); }
    private double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private String safe(String value) { return value == null ? "" : value.trim(); }
    private interface Value { double get(RadarSignal signal); }

    public static final class Score {
        private final int totalScore;
        private final String explanation;
        private final double velocityScore;
        private final double sourceBreadthScore;
        private final double sourceAuthorityScore;
        private final double noveltyScore;
        private final double crossPlatformSpreadScore;
        private final double persistenceScore;
        private final String lifecycleState;

        Score(int totalScore, String explanation, double velocityScore, double sourceBreadthScore,
              double sourceAuthorityScore, double noveltyScore, double crossPlatformSpreadScore,
              double persistenceScore, String lifecycleState) {
            this.totalScore = totalScore; this.explanation = explanation;
            this.velocityScore = velocityScore; this.sourceBreadthScore = sourceBreadthScore;
            this.sourceAuthorityScore = sourceAuthorityScore; this.noveltyScore = noveltyScore;
            this.crossPlatformSpreadScore = crossPlatformSpreadScore; this.persistenceScore = persistenceScore;
            this.lifecycleState = lifecycleState;
        }
        public int getTotalScore() { return totalScore; }
        public String getExplanation() { return explanation; }
        public double getVelocityScore() { return velocityScore; }
        public double getSourceBreadthScore() { return sourceBreadthScore; }
        public double getSourceAuthorityScore() { return sourceAuthorityScore; }
        public double getNoveltyScore() { return noveltyScore; }
        public double getCrossPlatformSpreadScore() { return crossPlatformSpreadScore; }
        public double getPersistenceScore() { return persistenceScore; }
        public String getLifecycleState() { return lifecycleState; }

        public RadarEventSnapshot toSnapshot(Long eventId, int signalCount, int sourceCount, LocalDateTime at) {
            RadarEventSnapshot snapshot = new RadarEventSnapshot();
            snapshot.setEventId(eventId); snapshot.setSnapshotAt(at); snapshot.setSignalCount(signalCount);
            snapshot.setIndependentSourceCount(sourceCount); snapshot.setVelocityScore(velocityScore);
            snapshot.setHotnessScore(totalScore); snapshot.setLifecycleState(lifecycleState);
            snapshot.setExplanation(explanation); return snapshot;
        }
    }
}
