package com.finscope.service.financials;

import com.finscope.domain.financials.FinancialFinding;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialMetric;
import com.finscope.domain.financials.FinancialQualityStatus;
import com.finscope.domain.financials.FinancialStatementType;
import com.finscope.domain.financials.FinancialValueOrigin;
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
