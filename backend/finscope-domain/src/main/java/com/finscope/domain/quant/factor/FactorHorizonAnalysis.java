package com.finscope.domain.quant.factor;

import lombok.Data;

/**
 * Deterministic cross-sectional evidence for one pre-registered holding horizon.
 */
@Data
public class FactorHorizonAnalysis {
    private int horizonDays;
    private int sampleCount;
    private int totalEligibleDays;
    private int minCrossSectionSize;
    private double coverageRatio;
    private double icMean;
    private double icStd;
    private double icIr;
    private double positiveIcRatio;
    private double negativeIcRatio;
    private double icMeanCiLower;
    private double icMeanCiUpper;
    private double favorableIcRatio;
    private double directionAdjustedIcMean;
    private double directionAdjustedCiLower;
    private double directionAdjustedCiUpper;
    private double directionAdjustedQuantileSpread;
    private double directionAdjustedMonotonicity;
}
