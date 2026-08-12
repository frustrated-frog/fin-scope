package com.finscope.service.radar;

import com.finscope.domain.radar.RadarSignal;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 识别转载和同源频道，提供热点计算使用的有效独立来源。 */
@Service
public class RadarSourceIndependenceService {
    private final RadarTextAnalyzer analyzer;

    public RadarSourceIndependenceService(RadarTextAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public Analysis analyze(List<RadarSignal> signals) {
        List<RadarSignal> values = signals == null ? Collections.<RadarSignal>emptyList() : signals;
        List<Observation> observations = new ArrayList<Observation>();
        for (RadarSignal signal : values) {
            if (signal == null) continue;
            RadarSignalFeatures features = analyzer.extract(signal);
            observations.add(new Observation(signal, features, sourceGroup(signal), repostFingerprint(features)));
        }
        if (observations.isEmpty()) return Analysis.empty();

        int[] parents = new int[observations.size()];
        for (int index = 0; index < parents.length; index++) parents[index] = index;
        for (int left = 0; left < observations.size(); left++) {
            for (int right = left + 1; right < observations.size(); right++) {
                if (sameEffectiveSource(observations.get(left), observations.get(right))) {
                    union(parents, left, right);
                }
            }
        }

        Set<Integer> independentSources = new HashSet<Integer>();
        Set<String> repostClusters = new HashSet<String>();
        boolean official = false;
        double authorityTotal = 0;
        for (int index = 0; index < observations.size(); index++) {
            Observation observation = observations.get(index);
            independentSources.add(find(parents, index));
            repostClusters.add(observation.repostFingerprint);
            official = official || isOfficial(observation.signal);
            authorityTotal += authority(observation.signal);
        }
        double authority = authorityTotal / observations.size();
        double concentration = clamp(1.0D - independentSources.size() / (double) observations.size());
        return new Analysis(observations, independentSources.size(), repostClusters.size(),
                concentration, official, authority);
    }

    private boolean sameEffectiveSource(Observation left, Observation right) {
        return left.sourceGroup.equals(right.sourceGroup)
                || left.repostFingerprint.equals(right.repostFingerprint);
    }

    private String sourceGroup(RadarSignal signal) {
        String provider = safe(signal.getProviderCode()).toUpperCase(Locale.ROOT);
        if (provider.isEmpty()) provider = safe(signal.getSourceName()).toUpperCase(Locale.ROOT);
        for (String suffix : new String[] { "_TELEGRAPH", "_APP", "_NEWS", "-TELEGRAPH", "-APP", "-NEWS" }) {
            if (provider.endsWith(suffix)) return provider.substring(0, provider.length() - suffix.length());
        }
        return provider.isEmpty() ? "UNKNOWN" : provider;
    }

    private String repostFingerprint(RadarSignalFeatures features) {
        String content = features.getNormalizedContent();
        String value = features.getNormalizedTitle() + "|" + (content.isEmpty() ? features.getNormalizedTitle() : content);
        return sha256(value);
    }

    private boolean isOfficial(RadarSignal signal) {
        String tier = safe(signal.getSourceTier()).toUpperCase(Locale.ROOT);
        String provider = safe(signal.getProviderCode()).toUpperCase(Locale.ROOT);
        return "OFFICIAL".equals(tier) || "SSE".equals(provider) || "SZSE".equals(provider)
                || "CNINFO".equals(provider) || "PBOC".equals(provider) || "CSRC".equals(provider);
    }

    private double authority(RadarSignal signal) {
        if (isOfficial(signal)) return 1.0D;
        if (signal.getSourceWeight() > 0) return clamp(signal.getSourceWeight());
        return RadarSourceQuality.resolve(signal.getSourceTier()).getHotnessWeight();
    }

    private int find(int[] parents, int value) {
        if (parents[value] != value) parents[value] = find(parents, parents[value]);
        return parents[value];
    }

    private void union(int[] parents, int left, int right) {
        int leftRoot = find(parents, left);
        int rightRoot = find(parents, right);
        if (leftRoot != rightRoot) parents[rightRoot] = leftRoot;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("无法生成转载指纹", error);
        }
    }

    private String safe(String value) { return value == null ? "" : value.trim(); }
    private double clamp(double value) { return Math.max(0, Math.min(1, value)); }

    public static final class Observation {
        private final RadarSignal signal;
        private final RadarSignalFeatures features;
        private final String sourceGroup;
        private final String repostFingerprint;

        Observation(RadarSignal signal, RadarSignalFeatures features, String sourceGroup, String repostFingerprint) {
            this.signal = signal;
            this.features = features;
            this.sourceGroup = sourceGroup;
            this.repostFingerprint = repostFingerprint;
        }

        public RadarSignal getSignal() { return signal; }
        public RadarSignalFeatures getFeatures() { return features; }
        public String getSourceGroup() { return sourceGroup; }
        public String getRepostFingerprint() { return repostFingerprint; }
    }

    public static final class Analysis {
        private final List<Observation> observations;
        private final int independentSourceCount;
        private final int repostClusterCount;
        private final double repostConcentration;
        private final boolean officialSource;
        private final double authorityScore;

        Analysis(List<Observation> observations, int independentSourceCount, int repostClusterCount,
                 double repostConcentration, boolean officialSource, double authorityScore) {
            this.observations = Collections.unmodifiableList(new ArrayList<Observation>(observations));
            this.independentSourceCount = independentSourceCount;
            this.repostClusterCount = repostClusterCount;
            this.repostConcentration = repostConcentration;
            this.officialSource = officialSource;
            this.authorityScore = authorityScore;
        }

        static Analysis empty() {
            return new Analysis(Collections.<Observation>emptyList(), 0, 0, 0, false, 0);
        }

        public List<Observation> getObservations() { return observations; }
        public int getIndependentSourceCount() { return independentSourceCount; }
        public int getRepostClusterCount() { return repostClusterCount; }
        public double getRepostConcentration() { return repostConcentration; }
        public boolean hasOfficialSource() { return officialSource; }
        public double getAuthorityScore() { return authorityScore; }
    }
}
