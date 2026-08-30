package com.finscope.domain.strategy.holding;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class HoldingStrategyDecision {
    private Long id;
    private String instrumentCode;
    private String instrumentName;
    private LocalDate decisionDate;
    private Long forecastRunId;
    private int horizonDays;
    private String modelVersion;
    private String dataFingerprint;
    private String action;
    private int suggestedQuantity;
    private double expectedEdgeAfterCost;
    private double p10RiskAmount;
    private double p90UpsideAmount;
    private double currentMarketValue;
    private double projectedWeight;
    private List<String> evidence = new ArrayList<String>();
    private List<String> blockers = new ArrayList<String>();
    private String explanation;
    private String benchmark;
    private String policyVersion;
    private String validationStatus;
    private LocalDate maturityDate;
    private Double strategyReturn;
    private Double holdReturn;
    private Double incrementalReturn;
    private String inputJson;
    private String outputJson;
    private LocalDateTime createdAt;
}
