package com.finscope.service.radar;

import com.finscope.domain.radar.RadarSignal;
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
        List<RadarSignal> values = signals == null ? Collections.<RadarSignal>emptyList() : signals;
        if (values.isEmpty()) return new Score(0, "暂无可评分信号");

        double quality = average(values, this::quality);
        double rank = average(values, this::rank);
        double recency = average(values, signal -> recency(signal, now));
        double diversity = uniqueProviders(values) / 3.0D;
        double clusterSize = Math.min(values.size(), 3) / 3.0D;
        int total = bounded(round(quality * 25 + rank * 25 + recency * 25
                + Math.min(1.0D, diversity) * 15 + clusterSize * 10));

        List<String> reasons = new ArrayList<String>();
        if (quality >= 0.75D) reasons.add("来源质量较高");
        if (rank >= 0.75D) reasons.add("来源排名靠前");
        if (recency >= 0.50D) reasons.add("信息仍在有效时效内");
        if (uniqueProviders(values) > 1) reasons.add("多源交叉覆盖");
        if (values.size() > 1) reasons.add("同一热点已聚合多条信号");
        if (reasons.isEmpty()) reasons.add("单一来源或较旧信息");
        return new Score(total, String.join("；", reasons));
    }

    private double quality(RadarSignal signal) {
        if (signal == null) return 0;
        if (signal.getSourceWeight() > 0) return clamp(signal.getSourceWeight());
        String tier = safe(signal.getSourceTier()).toUpperCase(Locale.ROOT);
        if ("TIER_1".equals(tier)) return 1.0D;
        if ("TIER_2".equals(tier)) return 0.75D;
        return 0.5D;
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

        Score(int totalScore, String explanation) {
            this.totalScore = totalScore; this.explanation = explanation;
        }
        public int getTotalScore() { return totalScore; }
        public String getExplanation() { return explanation; }
    }
}
