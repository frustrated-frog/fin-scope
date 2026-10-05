package com.finscope.domain.financials;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 与生成快照绑定的报告范围，所有字段来自本地底稿。 */
@Data
public class FinancialInterpretationScope {
    private String companyName;
    private String market;
    private String periodEnd;
    private String reportType;
    private String scope;
    private String currency;
    private String sourceCode;
    private int historicalReportCount;
    private int modelEvidenceCount;
    private List<String> comparablePeriods = new ArrayList<>();
    private List<String> materialLimitations = new ArrayList<>();
}
