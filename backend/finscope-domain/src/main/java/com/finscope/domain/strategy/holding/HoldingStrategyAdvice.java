package com.finscope.domain.strategy.holding;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HoldingStrategyAdvice {
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
}
