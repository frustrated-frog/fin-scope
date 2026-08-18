package com.finscope.domain.quant.academy;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuantStrategyAcademyCard {
    private Long candidateId;
    private String title;
    private String paperUrl;
    private String implementationUrl;
    private String adaptationNote;
    private List<String> mappedFactors = new ArrayList<String>();
    private Long datasetId;
    private String datasetName;
    private Long strategyVersionId;
    private Long experimentId;
    private String experimentStatus;
    private String evidenceLevel;
    private String shelf;
    private int evidenceScore;
    private String evidenceSummary;
    private String earningLogic;
    private String rationale;
    private String suitableRegime;
    private String invalidationRisk;
    private List<ScoreDimension> dimensions = new ArrayList<ScoreDimension>();
    private Metrics metrics;
    private List<YearEvidence> annualEvidence = new ArrayList<YearEvidence>();
    private List<String> limitations = new ArrayList<String>();

    @Data
    public static class ScoreDimension {
        private String code;
        private String label;
        private int score;
        private int maxScore;
        private String explanation;

        public ScoreDimension() {
        }

        public ScoreDimension(String code, String label, int score, int maxScore, String explanation) {
            this.code = code;
            this.label = label;
            this.score = score;
            this.maxScore = maxScore;
            this.explanation = explanation;
        }
    }

    @Data
    public static class Metrics {
        private double annualizedReturn;
        private double excessReturn;
        private double maxDrawdown;
        private double sharpeRatio;
        private double calmarRatio;
        private double turnover;
        private int tradeCount;
        private int yearCount;
        private double positiveExcessYearRatio;
    }

    @Data
    public static class YearEvidence {
        private int year;
        private double portfolioReturn;
        private double benchmarkReturn;
        private double excessReturn;
        private double maxDrawdown;
    }
}
