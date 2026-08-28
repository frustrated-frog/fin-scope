package com.finscope.domain.valuation;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ValuationMetricSummary {
    private String metricCode;
    private BigDecimal value;
    private BigDecimal percentile3y;
    private BigDecimal percentile5y;
    private int sampleCount3y;
    private int sampleCount5y;
    private String historyStatus;
}
