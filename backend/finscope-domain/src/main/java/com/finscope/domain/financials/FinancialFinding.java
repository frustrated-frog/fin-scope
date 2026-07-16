package com.finscope.domain.financials;

import lombok.Data;

@Data
public class FinancialFinding {
    private Long id;
    private Long reportId;
    private String ruleCode;
    private String ruleVersion;
    private String severity;
    private String direction;
    private String title;
    private String explanation;
    private String metricRefs;
    private String limitations;
}
