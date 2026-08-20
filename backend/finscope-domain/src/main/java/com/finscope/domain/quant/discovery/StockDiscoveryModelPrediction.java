package com.finscope.domain.quant.discovery;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StockDiscoveryModelPrediction {
    private Long id;
    private Long runId;
    private String instrumentCode;
    private LocalDate asOfDate;
    private int horizonDays;
    private String dataFingerprint;
    private String modelCode;
    private String modelName;
    private String modelVersion;
    private String role;
    private double calibratedProbability;
    private String shadowDecision;
    private String qualificationStatus;
    private String maturityStatus;
    private Double actualNetReturn;
    private String actualDirection;
    private Boolean predictionCorrect;
    private LocalDateTime settledAt;
}
