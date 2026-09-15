package com.finscope.domain.quant.execution;

import lombok.Data;

/** Frozen replay contract; times use Asia/Shanghai. */
@Data
public class TradingProtocol {
    private String version;
    private Integer holdingTradingDays;
    private Integer rebalanceTradingDays;
    private Integer slots;
    private Double maxExposure;
    private Double maxSingleWeight;
    private Double maxIndustryWeight;
    private java.time.LocalTime signalTime;
    private java.time.LocalTime executionTime;
    private Double initialCapital;
    private Double buyCommission;
    private Double sellCommission;
    private Double minimumCommission;
    private Double stampDuty;
    private Double slippageBps;
}
