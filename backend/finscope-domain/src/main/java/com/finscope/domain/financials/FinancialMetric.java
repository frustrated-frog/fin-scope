package com.finscope.domain.financials;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinancialMetric {
    private Long id;
    private Long reportId;
    private String metricCode;
    private String label;
    private BigDecimal value;
    private String unit;
    private String formulaVersion;
    private String inputRefs;
    private FinancialQualityStatus qualityStatus;
}
