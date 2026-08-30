package com.finscope.domain.strategy.holding;

import lombok.Data;

import java.time.LocalDate;

@Data
public class HoldingStrategyEvaluationRequest {
    private String instrumentCode;
    private LocalDate asOfDate;
    private int horizonDays;
    private double marketPrice;
    private double quantity;
    private double cash;
    private double totalEquity;
    private double currentWeight;
    private double upProbability;
    private double p10Return;
    private double p50Return;
    private double p90Return;
    private String forecastStatus;
    private String modelHealthStatus;
    private int quoteAgeDays;
    private double roundTripCostRate = 0.0015d;
    private double maxPositionWeight = 0.65d;
    private double cashBufferRate = 0.10d;
    private double minimumNetEdge = 0.005d;
    private int lotSize = 100;
    private Long forecastRunId;
    private String modelVersion;
    private String dataFingerprint;
    private Double costBasis;
    private Double unrealizedReturn;
}
