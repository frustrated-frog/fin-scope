package com.finscope.service.financials;

import com.finscope.dao.financials.FinancialReportRepository;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialMetric;
import com.finscope.domain.financials.FinancialQualityStatus;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.financials.FinancialStatementType;
import com.finscope.domain.financials.FinancialValueOrigin;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FinancialAnalysisPreflightTest {
    private final FinancialReportRepository reports = mock(FinancialReportRepository.class);
    private final FinancialAnalysisPreflight preflight =
            new FinancialAnalysisPreflight(new FinancialAnalysisEngine(), reports);

    @Test
    void recalculatesAndPersistsMetricsWhenStoredFormulaVersionIsStale() {
        FinancialReportView current = view(9L, LocalDate.of(2026, 3, 31), "financial-metrics-v1",
                line("REVENUE", "1200", FinancialStatementType.INCOME),
                line("OPERATING_COST", "720", FinancialStatementType.INCOME));
        FinancialReportView prior = view(8L, LocalDate.of(2025, 3, 31), null,
                line("REVENUE", "1000", FinancialStatementType.INCOME),
                line("OPERATING_COST", "650", FinancialStatementType.INCOME));

        assertTrue(preflight.requiresRefresh(current));
        FinancialReportView refreshed = preflight.ensureCurrent(current, Arrays.asList(prior));

        assertFalse(refreshed.getMetrics().isEmpty());
        assertTrue(refreshed.getMetrics().stream()
                .allMatch(value -> FinancialAnalysisEngine.FORMULA_VERSION.equals(value.getFormulaVersion())));
        assertEquals(new BigDecimal("20.000000"), refreshed.getMetrics().stream()
                .filter(value -> "REVENUE_YOY".equals(value.getMetricCode()))
                .findFirst().orElseThrow(AssertionError::new).getValue());
        verify(reports).replaceAnalysis(eq(9L), anyList(), anyList());
    }

    @Test
    void leavesCurrentMetricsUntouched() {
        FinancialReportView current = view(9L, LocalDate.of(2026, 3, 31),
                FinancialAnalysisEngine.FORMULA_VERSION,
                line("REVENUE", "1200", FinancialStatementType.INCOME));

        assertFalse(preflight.requiresRefresh(current));
        assertEquals(current, preflight.ensureCurrent(current, Collections.emptyList()));
        verify(reports, never()).replaceAnalysis(eq(9L), anyList(), anyList());
    }

    private FinancialReportView view(Long id, LocalDate periodEnd, String formulaVersion,
                                     FinancialLineItem... lines) {
        FinancialReport report = new FinancialReport();
        report.setId(id);
        report.setPeriodEnd(periodEnd);
        report.setReportType(FinancialReportType.Q1);
        report.setQualityStatus(FinancialQualityStatus.FRESH);
        FinancialReportView view = new FinancialReportView();
        view.setReport(report);
        view.getStatements().put(FinancialStatementType.INCOME,
                Arrays.stream(lines)
                        .filter(value -> value.getStatementType() == FinancialStatementType.INCOME)
                        .collect(java.util.stream.Collectors.toList()));
        view.getStatements().put(FinancialStatementType.BALANCE_SHEET,
                Arrays.stream(lines)
                        .filter(value -> value.getStatementType() == FinancialStatementType.BALANCE_SHEET)
                        .collect(java.util.stream.Collectors.toList()));
        view.getStatements().put(FinancialStatementType.CASH_FLOW,
                Arrays.stream(lines)
                        .filter(value -> value.getStatementType() == FinancialStatementType.CASH_FLOW)
                        .collect(java.util.stream.Collectors.toList()));
        if (formulaVersion != null) {
            FinancialMetric metric = new FinancialMetric();
            metric.setMetricCode("REVENUE_YOY");
            metric.setFormulaVersion(formulaVersion);
            view.getMetrics().add(metric);
        }
        return view;
    }

    private FinancialLineItem line(String code, String amount, FinancialStatementType type) {
        FinancialLineItem value = new FinancialLineItem();
        value.setConceptCode(code);
        value.setSourceLabel(code);
        value.setStatementType(type);
        value.setPeriodRole(type == FinancialStatementType.BALANCE_SHEET
                ? "CURRENT_PERIOD_END" : "CURRENT_YTD");
        value.setNormalizedValue(new BigDecimal(amount));
        value.setValueOrigin(FinancialValueOrigin.REPORTED);
        value.setQualityStatus(FinancialQualityStatus.FRESH);
        return value;
    }
}
