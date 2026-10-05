package com.finscope.common.enums.financials;

import lombok.Getter;

/** 财报研究报告的稳定章节标识，顺序同时作为阅读顺序。 */
@Getter
public enum FinancialInterpretationChapter {
    BUSINESS_MODEL("公司与商业模式"),
    PERFORMANCE_TRENDS("本期业绩与历史趋势"),
    GROWTH_DRIVERS("增长来源与持续性"),
    PROFITABILITY("盈利能力与费用"),
    EARNINGS_QUALITY("利润质量"),
    CASH_WORKING_CAPITAL("现金流与营运资金"),
    ASSET_QUALITY("资产质量"),
    SOLVENCY_FINANCING("偿债与融资"),
    CAPITAL_ALLOCATION("资本效率与资本配置"),
    DISCLOSURE_RISKS("披露、风险与后续验证");

    private final String label;

    FinancialInterpretationChapter(String label) {
        this.label = label;
    }
}
