package com.finscope.domain.quant.factor;

import lombok.Data;

/**
 * Compact robustness evidence. Turnover and cost values are explicit research proxies,
 * not simulated executable fills.
 */
@Data
public class FactorRobustnessReport {
    private String protocolVersion = "cross-sectional-robustness-v1";
    private int inSampleCount;
    private int outOfSampleCount;
    private double inSampleIcMean;
    private double outOfSampleIcMean;
    private double directionAdjustedInSampleIcMean;
    private double directionAdjustedOutOfSampleIcMean;
    private boolean outOfSampleDirectionAligned;
    private double rankTurnoverProxy;
    private double netQuantileSpreadAt10Bps;
    private double netQuantileSpreadAt30Bps;
    private String costModel = "rank-turnover-proxy-v1";
}
