package com.finscope.web.response.strategy;

import com.finscope.domain.strategy.holding.HoldingStrategyDecision;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class HoldingStrategyDecisionResponse {
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
    private LocalDateTime createdAt;

    public static HoldingStrategyDecisionResponse of(HoldingStrategyDecision value) {
        HoldingStrategyDecisionResponse response = new HoldingStrategyDecisionResponse();
        response.id = value.getId();
        response.instrumentCode = value.getInstrumentCode();
        response.instrumentName = value.getInstrumentName();
        response.decisionDate = value.getDecisionDate();
        response.forecastRunId = value.getForecastRunId();
        response.horizonDays = value.getHorizonDays();
        response.modelVersion = value.getModelVersion();
        response.dataFingerprint = value.getDataFingerprint();
        response.action = value.getAction();
        response.suggestedQuantity = value.getSuggestedQuantity();
        response.expectedEdgeAfterCost = value.getExpectedEdgeAfterCost();
        response.p10RiskAmount = value.getP10RiskAmount();
        response.p90UpsideAmount = value.getP90UpsideAmount();
        response.currentMarketValue = value.getCurrentMarketValue();
        response.projectedWeight = value.getProjectedWeight();
        response.evidence = value.getEvidence();
        response.blockers = value.getBlockers();
        response.explanation = value.getExplanation();
        response.benchmark = value.getBenchmark();
        response.policyVersion = value.getPolicyVersion();
        response.validationStatus = value.getValidationStatus();
        response.maturityDate = value.getMaturityDate();
        response.strategyReturn = value.getStrategyReturn();
        response.holdReturn = value.getHoldReturn();
        response.incrementalReturn = value.getIncrementalReturn();
        response.createdAt = value.getCreatedAt();
        return response;
    }
}
