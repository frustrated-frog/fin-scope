package com.finscope.domain.quant.forecast;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ForecastCandidateRun {
    private Long id;
    private Long forecastRunId;
    private String instrumentCode;
    private LocalDate asOfDate;
    private int horizonDays;
    private String dataFingerprint;
    private String modelCode;
    private String modelName;
    private String modelVersion;
    private String role;
    private Double rawProbability;
    private Double calibratedProbability;
    private String shadowDecision;
    private String qualificationStatus;
    private int lockedSampleCount;
    private double lockedAccuracy;
    private double lockedBrierScore;
    private double lockedLogLoss;
    private double lockedBrierSkillScore;
    private String maturityStatus = "PENDING";
    private Double actualNetReturn;
    private String actualDirection;
    private Boolean predictionCorrect;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;
}
