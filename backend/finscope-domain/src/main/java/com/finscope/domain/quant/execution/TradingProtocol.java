package com.finscope.domain.quant.execution;

import lombok.Data;

/** Frozen replay contract; times use Asia/Shanghai. */
@Data
public class TradingProtocol {
    private String version;
    private int holdingTradingDays;
    private int rebalanceTradingDays;
    private int slots;
    private double maxExposure;
    private double maxSingleWeight;
    private double maxIndustryWeight;
    private java.time.LocalTime signalTime;
    private java.time.LocalTime executionTime;
    private double initialCapital;
    private double buyCommission;
    private double sellCommission;
    private double minimumCommission;
    private double stampDuty;
    private double slippageBps;
}
