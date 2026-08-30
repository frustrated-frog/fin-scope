package com.finscope.domain.marketpulse;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MarketInternalHistoryPoint {
    private LocalDate businessDate;
    private Integer advanceCount;
    private Integer declineCount;
    private Integer flatCount;
    private Integer validCount;
    private Double advanceRatio;
    private Double totalAmount;
    private Double medianChangePct;
    private Double ma20Ratio;
    private Double ma60Ratio;
    private Double ma120Ratio;
    private Double ma250Ratio;
    private Integer newHigh20Count;
    private Integer newLow20Count;
    private Integer newHigh60Count;
    private Integer newLow60Count;
    private Integer newHigh250Count;
    private Integer newLow250Count;
    private Integer netAdvances;
    private Integer advanceDeclineLine;
    private Double advanceAmount;
    private Double declineAmount;
    private Double flatAmount;
    private Double advanceAmountRatio;
    private Double netAdvancingAmount;
    private Double trin;
    private Double mcclellanOscillator;
    private Double breadthThrustRatio;
}
