package com.finscope.domain.quant.forecast;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class ForecastModelRace {
    private String instrumentCode;
    private int horizonDays;
    private String status;
    private int sampleCount;
    private int minimumPromotionSamples;
    private String championCode;
    private String promotionCandidateCode;
    private String conclusion;
    private LocalDate firstAsOfDate;
    private LocalDate lastAsOfDate;
    private List<CandidateMetric> candidates = new ArrayList<CandidateMetric>();

    @Data
    public static class CandidateMetric {
        private String modelCode;
        private String modelName;
        private String role;
        private int sampleCount;
        private double brierScore;
        private double logLoss;
        private double brierSkillScore;
        private int coveredCount;
        private double coverage;
        private Double coveredAccuracy;
        private double brierDeltaVsChampion;
        private double logLossDeltaVsChampion;
        private boolean promotionEligible;
    }
}
