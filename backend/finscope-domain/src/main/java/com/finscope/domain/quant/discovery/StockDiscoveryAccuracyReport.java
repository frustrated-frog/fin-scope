package com.finscope.domain.quant.discovery;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StockDiscoveryAccuracyReport {
    private String schemaVersion;
    private String asOfDate;
    private int horizonDays;
    private String status;
    private String conclusion;
    private int maturedRunCount;
    private int maturedCandidateCount;
    private int maturedFinalCount;
    private int pendingCount;
    private ProbabilityQuality probabilityQuality;
    private List<ReliabilityBin> reliabilityBins = new ArrayList<>();
    private List<SelectionMetric> selectionMetrics = new ArrayList<>();
    private List<WindowMetric> windows = new ArrayList<>();
    private List<SectorPerformance> sectorPerformance = new ArrayList<>();
    private ModelRace modelRace;
    private List<RecentOutcome> recentOutcomes = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class ProbabilityQuality {
        private int sampleCount;
        private Double brierScore;
        private Double brierSkillScore;
        private Double logLoss;
        private Double accuracy;
        private Double expectedCalibrationError;
        private Double baselineProbability;
    }

    @Data
    public static class ReliabilityBin {
        private double lowerBound;
        private double upperBound;
        private int count;
        private Double meanProbability;
        private Double observedUpRate;
        private Double calibrationError;
    }

    @Data
    public static class SelectionMetric {
        private int limit;
        private int maturedRunCount;
        private int sampleCount;
        private Double hitRate;
        private Double averageNetReturn;
        private Double medianNetReturn;
        private Double admittedPoolAverageReturn;
        private Double averageExcessVsAdmittedPool;
    }

    @Data
    public static class WindowMetric {
        private int windowDays;
        private String startDate;
        private int maturedRunCount;
        private int probabilitySampleCount;
        private int finalCount;
        private Double finalHitRate;
        private Double finalAverageNetReturn;
        private Double brierSkillScore;
    }

    @Data
    public static class SectorPerformance {
        private String sectorName;
        private int sampleCount;
        private double hitRate;
        private double averageNetReturn;
    }

    @Data
    public static class ModelRace {
        private String status;
        private int sampleCount;
        private int minimumPromotionSamples;
        private String championCode;
        private String promotionCandidateCode;
        private String conclusion;
        private List<ModelMetric> candidates = new ArrayList<>();
    }

    @Data
    public static class ModelMetric {
        private String modelCode;
        private String modelName;
        private String role;
        private int sampleCount;
        private double brierScore;
        private double logLoss;
        private int coveredCount;
        private double coverage;
        private Double coveredAccuracy;
        private double brierDeltaVsChampion;
        private double logLossDeltaVsChampion;
        private boolean promotionEligible;
    }

    @Data
    public static class RecentOutcome {
        private Long runId;
        private String instrumentCode;
        private String asOfDate;
        private int finalRank;
        private Double calibratedProbability;
        private double actualNetReturn;
        private String actualDirection;
        private List<String> sectorNames = new ArrayList<>();
    }
}
