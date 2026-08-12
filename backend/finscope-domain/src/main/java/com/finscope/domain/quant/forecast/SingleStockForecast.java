package com.finscope.domain.quant.forecast;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class SingleStockForecast {
    private String reportSchemaVersion;
    private String modelVersion;
    private String instrumentCode;
    private LocalDate asOfDate;
    private int horizonDays;
    private String status;
    private String conclusion;
    private String decision;
    private String decisionReason;
    private Integer barCount;
    private Integer labeledSampleCount;
    private Double upProbability;
    private Double rawProbability;
    private ConfidenceInterval probabilityInterval;
    private Double expectedNetReturn;
    private Double lowerNetReturn;
    private Double upperNetReturn;
    private String dataFingerprint;
    private String sourceCode;
    private String sourceFamily;
    private String qualityStatus;
    private Double lastClose;
    private StrategyPolicy strategyPolicy;
    private Validation validation;
    private List<FactorExplanation> factorExplanations = new ArrayList<FactorExplanation>();
    private PerformanceReport performance;
    private List<EquityPoint> equityCurve = new ArrayList<EquityPoint>();
    private List<AnnualPerformance> annualPerformance = new ArrayList<AnnualPerformance>();
    private List<RegimePerformance> regimePerformance = new ArrayList<RegimePerformance>();
    private EvaluationSlice inSample;
    private EvaluationSlice outOfSample;
    private ParameterStability parameterStability;
    private List<Observation> recentObservations = new ArrayList<Observation>();
    private ModelQualification qualification;
    private SelectiveValidation selectiveValidation;
    private ForecastContext context;
    private ModelCompetition modelCompetition;
    private LeakageAudit leakageAudit;
    private QlibReference qlibReference;
    private List<String> warnings = new ArrayList<String>();

    @Data
    public static class Validation {
        private int outOfSampleCount;
        private int independentSampleCount;
        private double accuracy;
        private double brierScore;
        private double baselineBrierScore;
        private double observedUpRate;
    }

    @Data
    public static class Observation {
        private LocalDate signalDate;
        private double probability;
        private double actualNetReturn;
        private boolean correct;
    }

    @Data
    public static class StrategyPolicy {
        private double signalThreshold;
        private int holdingDays;
        private String entryRule;
        private String exitRule;
        private String overlapPolicy;
        private double roundTripCostRate;
        private String benchmark;
    }

    @Data
    public static class FactorExplanation {
        private String code;
        private String name;
        private String category;
        private String formula;
        private String window;
        private double currentValue;
        private double historicalPercentile;
        private double standardizedValue;
        private double coefficient;
        private double contribution;
        private String direction;
        private String economicMeaning;
        private String boundary;
    }

    @Data
    public static class PerformanceSummary {
        private double totalReturn;
        private double annualizedReturn;
        private double annualizedVolatility;
        private double sharpeRatio;
        private double dailyWinRate;
        private double maxDrawdown;
        private LocalDate maxDrawdownStartDate;
        private LocalDate maxDrawdownTroughDate;
        private LocalDate maxDrawdownRecoveryDate;
        private int maxDrawdownDurationDays;
    }

    @Data
    public static class TradeSummary {
        private LocalDate signalDate;
        private LocalDate entryDate;
        private LocalDate exitDate;
        private double probability;
        private double netReturn;
        private double cost;
        private int holdingDays;
    }

    @Data
    public static class PerformanceReport {
        private String benchmarkLabel;
        private PerformanceSummary strategy;
        private PerformanceSummary benchmark;
        private double excessReturn;
        private int tradeCount;
        private double profitableTradeRate;
        private double turnover;
        private double totalCost;
        private double holdingTimeRatio;
        private double averageHoldingDays;
        private List<TradeSummary> trades = new ArrayList<TradeSummary>();
    }

    @Data
    public static class EquityPoint {
        private LocalDate tradeDate;
        private double strategyNav;
        private double benchmarkNav;
        private double drawdown;
        private boolean invested;
    }

    @Data
    public static class AnnualPerformance {
        private int year;
        private double strategyReturn;
        private double benchmarkReturn;
        private double excessReturn;
        private double maxDrawdown;
        private int tradeCount;
    }

    @Data
    public static class RegimePerformance {
        private String regime;
        private String label;
        private int sampleDays;
        private double strategyReturn;
        private double benchmarkReturn;
        private double excessReturn;
        private double sharpeRatio;
        private double maxDrawdown;
        private int tradeCount;
        private double holdingTimeRatio;
    }

    @Data
    public static class EvaluationSlice {
        private int sampleCount;
        private double accuracy;
        private double brierScore;
        private Double baselineBrierScore;
        private String evidenceRole;
    }

    @Data
    public static class StabilityScenario {
        private int holdingDays;
        private double threshold;
        private boolean primary;
        private double annualizedReturn;
        private double excessReturn;
        private double sharpeRatio;
        private double maxDrawdown;
        private int tradeCount;
    }

    @Data
    public static class ParameterStability {
        private List<StabilityScenario> scenarios = new ArrayList<StabilityScenario>();
        private double positiveExcessRatio;
        private double worstExcessReturn;
        private double worstSharpeRatio;
    }

    @Data
    public static class ConfidenceInterval {
        private String status;
        private Double lower;
        private Double upper;
        private double confidenceLevel;
        private String method;
        private int validIterations;
        private String reason;
        private String limitation;
    }

    @Data
    public static class SplitSliceAudit {
        private LocalDate startDate;
        private LocalDate endDate;
        private int sampleCount;
        private int independentSampleCount;
        private int positiveCount;
        private int purgedCount;
    }

    @Data
    public static class QualificationSplitAudit {
        private SplitSliceAudit development;
        private SplitSliceAudit calibration;
        private SplitSliceAudit lockedTest;
        private int labelHorizonDays;
        private int independentStrideDays;
        private String rule;
    }

    @Data
    public static class ProbabilityMetricSet {
        private int sampleCount;
        private double accuracy;
        private double brierScore;
        private double baselineBrierScore;
        private double brierSkillScore;
        private double logLoss;
        private double expectedCalibrationError;
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
    public static class CalibrationReport {
        private String status;
        private String method;
        private int sampleCount;
        private int positiveCount;
        private double slope;
        private double intercept;
        private double rawLogLoss;
        private double calibratedLogLoss;
        private String reason;
    }

    @Data
    public static class LockedTestReport {
        private double baselineProbability;
        private ProbabilityMetricSet rawMetrics;
        private ProbabilityMetricSet calibratedMetrics;
        private ProbabilityMetricSet baselineMetrics;
        private List<ReliabilityBin> reliabilityBins = new ArrayList<ReliabilityBin>();
    }

    @Data
    public static class SelectiveValidation {
        private double lowerThreshold;
        private double upperThreshold;
        private int sampleCount;
        private int coveredCount;
        private double coverage;
        private double coveredAccuracy;
        private double abstainRate;
    }

    @Data
    public static class ContextSource {
        private String code;
        private String label;
        private String status;
        private double coverage;
        private String regime;
        private String reason;
    }

    @Data
    public static class ForecastContext {
        private ContextSource market;
        private ContextSource industry;
        private List<String> featureCodes = new ArrayList<String>();
        private String alignmentRule;
    }

    @Data
    public static class ModelCandidate {
        private String code;
        private String name;
        private boolean selected;
        private int selectionSampleCount;
        private double accuracy;
        private double brierScore;
        private double logLoss;
        private double baselineBrierScore;
        private String reason;
    }

    @Data
    public static class ModelCompetition {
        private String selectedModel;
        private LocalDate selectionEndDate;
        private LocalDate calibrationStartDate;
        private String selectionRule;
        private List<ModelCandidate> candidates = new ArrayList<ModelCandidate>();
    }

    @Data
    public static class LeakageAudit {
        private String status;
        private int checkedSampleCount;
        private List<String> checks = new ArrayList<String>();
    }

    @Data
    public static class QlibReference {
        private String status;
        private String role;
        private boolean runtimeDependency;
    }

    @Data
    public static class QualificationIntervals {
        private ConfidenceInterval brierSkillScore;
        private ConfidenceInterval accuracy;
        private ConfidenceInterval excessReturn;
        private ConfidenceInterval sharpeRatio;
    }

    @Data
    public static class TrialIdentity {
        private String trialId;
        private String featureVersion;
        private String labelVersion;
        private String splitVersion;
        private String calibrationVersion;
        private String bootstrapVersion;
        private long randomSeed;
        private String modelVersion;
    }

    @Data
    public static class ModelQualification {
        private String status;
        private String reason;
        private TrialIdentity trial;
        private QualificationSplitAudit splitAudit;
        private CalibrationReport calibration;
        private LockedTestReport lockedTest;
        private QualificationIntervals confidenceIntervals;
    }
}
