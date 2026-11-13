package com.finscope.common.enums.fundamentals;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FundamentalBusinessAspect {
    REVENUE_MODEL("PROFIT_MODEL", "revenue_engine", "收入从哪里来", "revenueModel"),
    CUSTOMER_CASH("PROFIT_MODEL", "cash_quality", "回款与现金", "customers"),
    COST_DRIVERS("PROFIT_MODEL", "profit_engine", "利润与成本", "costDrivers"),
    COMPETITIVE_ADVANTAGE("COMPETITION", "moat", "竞争优势及其依据", "advantage");

    private final String dimension;
    private final String sectionKey;
    private final String label;
    private final String noteField;
}
