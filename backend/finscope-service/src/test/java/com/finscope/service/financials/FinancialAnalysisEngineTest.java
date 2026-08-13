package com.finscope.service.financials;

import com.finscope.domain.financials.FinancialFinding;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialMetric;
import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.financials.FinancialStatementType;
import com.finscope.common.enums.financials.FinancialValueOrigin;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialAnalysisEngineTest {
    private final FinancialAnalysisEngine engine = new FinancialAnalysisEngine();

    @Test
    void calculatesCoreMetricsAndFlagsReceivableAndCashDivergence() {
        FinancialAnalysisResult result = engine.analyze(
                Arrays.asList(
                        line("REVENUE", "1100", FinancialStatementType.INCOME),
                        line("OPERATING_COST", "660", FinancialStatementType.INCOME),
                        line("NET_PROFIT_PARENT", "200", FinancialStatementType.INCOME),
                        line("OPERATING_CASH_FLOW", "80", FinancialStatementType.CASH_FLOW),
                        line("ACCOUNTS_RECEIVABLE", "270", FinancialStatementType.BALANCE_SHEET),
                        line("INVENTORY", "180", FinancialStatementType.BALANCE_SHEET),
                        line("TOTAL_ASSETS", "3000", FinancialStatementType.BALANCE_SHEET),
                        line("TOTAL_LIABILITIES", "1200", FinancialStatementType.BALANCE_SHEET)
                ),
                Arrays.asList(
                        line("REVENUE", "1000", FinancialStatementType.INCOME),
                        line("ACCOUNTS_RECEIVABLE", "200", FinancialStatementType.BALANCE_SHEET),
                        line("INVENTORY", "170", FinancialStatementType.BALANCE_SHEET)
                )
        );

        assertEquals(new BigDecimal("10.000000"), metric(result, "REVENUE_YOY"));
        assertEquals(new BigDecimal("40.000000"), metric(result, "GROSS_MARGIN"));
        assertEquals(new BigDecimal("40.000000"), metric(result, "OPERATING_CASH_TO_NET_PROFIT"));
        assertEquals(new BigDecimal("40.000000"), metric(result, "DEBT_TO_ASSETS"));
        List<String> rules = result.getFindings().stream()
                .map(FinancialFinding::getRuleCode)
                .collect(Collectors.toList());
        assertTrue(rules.contains("RECEIVABLES_OUTPACE_REVENUE"));
        assertTrue(rules.contains("PROFIT_CASH_DIVERGENCE"));
    }

    @Test
    void reportsMissingInputsInsteadOfInventingMetrics() {
        FinancialAnalysisResult result = engine.analyze(
                Arrays.asList(line("REVENUE", "100", FinancialStatementType.INCOME)),
                new ArrayList<FinancialLineItem>());

        assertTrue(result.getMetrics().stream()
                .map(FinancialMetric::getMetricCode)
                .noneMatch("REVENUE_YOY"::equals));
        assertTrue(result.getDataGaps().contains("缺少上年同期营业收入，无法计算营收同比"));
    }

    @Test
    void calculatesProfitLiquidityDebtAndFreeCashFlowMetrics() {
        FinancialAnalysisResult result = engine.analyze(
                Arrays.asList(
                        line("REVENUE", "1200", FinancialStatementType.INCOME),
                        line("OPERATING_COST", "720", FinancialStatementType.INCOME),
                        line("NET_PROFIT_PARENT", "250", FinancialStatementType.INCOME),
                        line("SELLING_EXPENSE", "30", FinancialStatementType.INCOME),
                        line("ADMIN_EXPENSE", "40", FinancialStatementType.INCOME),
                        line("RND_EXPENSE", "30", FinancialStatementType.INCOME),
                        line("FINANCE_EXPENSE", "10", FinancialStatementType.INCOME),
                        line("OPERATING_CASH_FLOW", "100", FinancialStatementType.CASH_FLOW),
                        line("CAPITAL_EXPENDITURE", "40", FinancialStatementType.CASH_FLOW),
                        line("TOTAL_CURRENT_ASSETS", "900", FinancialStatementType.BALANCE_SHEET),
                        line("TOTAL_CURRENT_LIABILITIES", "600", FinancialStatementType.BALANCE_SHEET),
                        line("INVENTORY", "180", FinancialStatementType.BALANCE_SHEET),
                        line("SHORT_TERM_BORROWINGS", "200", FinancialStatementType.BALANCE_SHEET),
                        line("CURRENT_PORTION_LONG_DEBT", "50", FinancialStatementType.BALANCE_SHEET),
                        line("LONG_TERM_BORROWINGS", "150", FinancialStatementType.BALANCE_SHEET),
                        line("BONDS_PAYABLE", "100", FinancialStatementType.BALANCE_SHEET),
                        line("CONTRACT_LIABILITIES", "180", FinancialStatementType.BALANCE_SHEET),
                        line("TOTAL_ASSETS", "1600", FinancialStatementType.BALANCE_SHEET),
                        line("TOTAL_LIABILITIES", "600", FinancialStatementType.BALANCE_SHEET),
                        line("TOTAL_EQUITY", "1000", FinancialStatementType.BALANCE_SHEET)
                ),
                Arrays.asList(
                        line("REVENUE", "1000", FinancialStatementType.INCOME),
                        line("OPERATING_COST", "650", FinancialStatementType.INCOME),
                        line("NET_PROFIT_PARENT", "200", FinancialStatementType.INCOME),
                        line("OPERATING_CASH_FLOW", "80", FinancialStatementType.CASH_FLOW),
                        line("CONTRACT_LIABILITIES", "120", FinancialStatementType.BALANCE_SHEET)
                ));

        assertEquals(new BigDecimal("25.000000"), metric(result, "NET_PROFIT_PARENT_YOY"));
        assertEquals(new BigDecimal("25.000000"), metric(result, "OPERATING_CASH_FLOW_YOY"));
        assertEquals(new BigDecimal("150.000000"), metric(result, "CURRENT_RATIO"));
        assertEquals(new BigDecimal("120.000000"), metric(result, "QUICK_RATIO"));
        assertEquals(new BigDecimal("500"), metric(result, "INTEREST_BEARING_DEBT"));
        assertEquals(new BigDecimal("40"), metric(result, "CAPITAL_EXPENDITURE"));
        assertEquals(new BigDecimal("60"), metric(result, "FREE_CASH_FLOW"));
        assertEquals(new BigDecimal("9.166667"), metric(result, "PERIOD_EXPENSE_RATIO"));
        assertEquals(new BigDecimal("5.000000"), metric(result, "GROSS_MARGIN_YOY_CHANGE"));
        assertEquals(new BigDecimal("0.833333"), metric(result, "NET_MARGIN_YOY_CHANGE"));
        assertEquals(new BigDecimal("50.000000"), metric(result, "CONTRACT_LIABILITIES_YOY"));
        assertEquals(new BigDecimal("0"), metric(result, "BALANCE_SHEET_IDENTITY_GAP"));
    }

    @Test
    void omitsFreeCashFlowWhenCapitalExpenditureIsMissing() {
        FinancialAnalysisResult result = engine.analyze(
                Arrays.asList(
                        line("REVENUE", "100", FinancialStatementType.INCOME),
                        line("OPERATING_CASH_FLOW", "60", FinancialStatementType.CASH_FLOW)),
                Arrays.asList(line("REVENUE", "90", FinancialStatementType.INCOME)));

        assertTrue(result.getMetrics().stream()
                .map(FinancialMetric::getMetricCode)
                .noneMatch("FREE_CASH_FLOW"::equals));
        assertTrue(result.getDataGaps().contains("缺少资本开支，无法计算自由现金流"));
    }

    @Test
    void keepsTheReportCurrencyForMonetaryMetrics() {
        FinancialLineItem operatingCash = line(
                "OPERATING_CASH_FLOW", "100", FinancialStatementType.CASH_FLOW);
        operatingCash.setCurrency("USD");
        FinancialLineItem capitalExpenditure = line(
                "CAPITAL_EXPENDITURE", "40", FinancialStatementType.CASH_FLOW);
        capitalExpenditure.setCurrency("USD");

        FinancialAnalysisResult result = engine.analyze(
                Arrays.asList(operatingCash, capitalExpenditure),
                new ArrayList<FinancialLineItem>());

        assertEquals("USD", result.getMetrics().stream()
                .filter(value -> "FREE_CASH_FLOW".equals(value.getMetricCode()))
                .findFirst().orElseThrow(AssertionError::new).getUnit());
    }

    @Test
    void acceptsLegacyProviderConceptAliasesWhenRecalculatingStoredReports() {
        FinancialAnalysisResult result = engine.analyze(
                Arrays.asList(
                        line("REVENUE", "1200", FinancialStatementType.INCOME),
                        line("TOTAL_CURRENT_ASSETS", "900", FinancialStatementType.BALANCE_SHEET),
                        line("TOTAL_CURRENT_LIAB", "600", FinancialStatementType.BALANCE_SHEET),
                        line("INVENTORY", "180", FinancialStatementType.BALANCE_SHEET),
                        line("CONTRACT_LIAB", "180", FinancialStatementType.BALANCE_SHEET)),
                Arrays.asList(
                        line("REVENUE", "1000", FinancialStatementType.INCOME),
                        line("CONTRACT_LIAB", "120", FinancialStatementType.BALANCE_SHEET)));

        assertEquals(new BigDecimal("150.000000"), metric(result, "CURRENT_RATIO"));
        assertEquals(new BigDecimal("120.000000"), metric(result, "QUICK_RATIO"));
        assertEquals(new BigDecimal("50.000000"), metric(result, "CONTRACT_LIABILITIES_YOY"));
    }

    private BigDecimal metric(FinancialAnalysisResult result, String code) {
        return result.getMetrics().stream()
                .filter(value -> code.equals(value.getMetricCode()))
                .findFirst()
                .orElseThrow(AssertionError::new)
                .getValue();
    }

    private FinancialLineItem line(String code, String value, FinancialStatementType type) {
        FinancialLineItem item = new FinancialLineItem();
        item.setConceptCode(code);
        item.setSourceLabel(code);
        item.setStatementType(type);
        item.setPeriodRole(type == FinancialStatementType.BALANCE_SHEET
                ? "CURRENT_PERIOD_END" : "CURRENT_YTD");
        item.setNormalizedValue(new BigDecimal(value));
        item.setValueOrigin(FinancialValueOrigin.REPORTED);
        item.setQualityStatus(FinancialQualityStatus.FRESH);
        return item;
    }
}
