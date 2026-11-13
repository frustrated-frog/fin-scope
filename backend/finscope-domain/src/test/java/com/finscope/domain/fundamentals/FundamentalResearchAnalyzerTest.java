package com.finscope.domain.fundamentals;

import com.finscope.common.enums.financials.*;
import com.finscope.common.enums.fundamentals.FundamentalDimension;
import com.finscope.domain.financials.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FundamentalResearchAnalyzerTest {
    @Test
    void usesConsolidatedProfitRatherThanParentProfitAndKeepsCumulativePeriod() {
        var current = report("2026-09-30", "CNY");
        lines(current, line("NET_PROFIT", "100"), line("NET_PROFIT_PARENT", "10"), line("OPERATING_CASH_FLOW", "75"));
        var result = new FundamentalResearchAnalyzer().analyze(current, null);
        var earnings = result.stream().filter(value -> value.getDimension() == FundamentalDimension.EARNINGS_QUALITY).findFirst().orElseThrow();
        assertFalse(earnings.isAttention());
        var ratio = earnings.getMetrics().stream().filter(value -> value.getConceptCode().equals("CASH_CONVERSION")).findFirst().orElseThrow();
        assertEquals(0, ratio.getValue().compareTo(new BigDecimal("75")));
        assertEquals(LocalDate.parse("2026-09-30"), ratio.getPeriodEnd());
        assertEquals(42L, ratio.getReportId());
    }

    @Test
    void missingOrNonPositiveConsolidatedProfitNeverProducesCashConversion() {
        var current = report("2026-09-30", "CNY");
        for (String profit : List.of("-10", "0")) {
            lines(current, line("NET_PROFIT", profit), line("OPERATING_CASH_FLOW", "20"));
            assertTrue(new FundamentalResearchAnalyzer().analyze(current, null).stream().flatMap(value -> value.getMetrics().stream())
                    .noneMatch(value -> value.getConceptCode().equals("CASH_CONVERSION")));
        }
        lines(current, line("NET_PROFIT_PARENT", "10"), line("OPERATING_CASH_FLOW", "20"));
        assertTrue(new FundamentalResearchAnalyzer().analyze(current, null).get(1).getConclusion().contains("缺少合并净利润"));
    }

    @Test
    void comparesOnlySamePeriodScopeCurrencyAndSourceAndSkipsConflictingFacts() {
        var current = report("2026-09-30", "CNY");
        var previous = report("2025-09-30", "CNY");
        lines(current, line("REVENUE", "120"));
        lines(previous, line("REVENUE", "100"));
        assertEquals(2, new FundamentalResearchAnalyzer().analyze(current, previous).get(0).getMetrics().size());
        previous.getReport().setCurrency("USD");
        assertEquals(1, new FundamentalResearchAnalyzer().analyze(current, previous).get(0).getMetrics().size());
        previous.getReport().setCurrency("CNY");
        previous.getReport().setScope("PARENT");
        assertEquals(1, new FundamentalResearchAnalyzer().analyze(current, previous).get(0).getMetrics().size());
        lines(current, line("REVENUE", "120"), line("REVENUE", "121"));
        assertTrue(new FundamentalResearchAnalyzer().analyze(current, previous).get(0).getMetrics().isEmpty());
    }

    @Test
    void refusesAmountsFromOtherCurrenciesSourcesAndUnverifiedLines() {
        var current = report("2026-09-30", "CNY");
        var foreign = line("OPERATING_CASH_FLOW", "100");
        foreign.setCurrency("USD");
        var otherSource = line("REVENUE", "900");
        otherSource.setSourceCode("OTHER");
        var unverified = line("NET_PROFIT", "30");
        unverified.setQualityStatus(FinancialQualityStatus.UNVERIFIED);
        lines(current, foreign, otherSource, unverified);
        assertTrue(new FundamentalResearchAnalyzer().analyze(current, null).stream().allMatch(value -> value.getMetrics().isEmpty()));
    }

    @Test
    void negativeCashCreatesFollowupWithoutClaimingFraudAndIgnoresQuarterRole() {
        var current = report("2026-09-30", "CNY");
        var quarter = line("OPERATING_CASH_FLOW", "999");
        quarter.setPeriodRole("CURRENT_QUARTER");
        lines(current, line("NET_PROFIT", "10"), line("OPERATING_CASH_FLOW", "-3"), quarter, line("CAPITAL_EXPENDITURE", "2"));
        var result = new FundamentalResearchAnalyzer().analyze(current, null);
        assertTrue(result.get(1).isAttention());
        assertTrue(result.get(1).getConclusion().contains("净流出"));
        assertFalse(result.get(1).getConclusion().contains("造假"));
        assertEquals(0, result.get(3).getMetrics().stream().filter(value -> value.getConceptCode().equals("CASH_AFTER_CAPEX"))
                .findFirst().orElseThrow().getValue().compareTo(new BigDecimal("-5")));
    }

    private FinancialReportView report(String period, String currency) {
        var view = new FinancialReportView();
        var report = new FinancialReport();
        report.setId(42L);
        report.setInstrumentId(5L);
        report.setPeriodEnd(LocalDate.parse(period));
        report.setReportType(FinancialReportType.Q3);
        report.setCurrency(currency);
        report.setScope("CONSOLIDATED");
        report.setSourceCode("SOURCE");
        view.setReport(report);
        return view;
    }

    private FinancialLineItem line(String code, String amount) {
        var value = new FinancialLineItem();
        value.setConceptCode(code);
        value.setNormalizedValue(new BigDecimal(amount));
        value.setStatementType(FinancialStatementType.INCOME);
        value.setCurrency("CNY");
        value.setSourceCode("SOURCE");
        value.setPeriodRole("CURRENT_YTD");
        value.setQualityStatus(FinancialQualityStatus.FRESH);
        return value;
    }

    private void lines(FinancialReportView view, FinancialLineItem... values) {
        view.getStatements().put(FinancialStatementType.INCOME, List.of(values));
    }
}
