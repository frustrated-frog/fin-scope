package com.finscope.domain.fundamentals;

import com.finscope.common.enums.fundamentals.FundamentalComparisonState;
import com.finscope.common.enums.fundamentals.FundamentalDimension;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FundamentalFinancialComparisonTest {
    private final FundamentalFinancialComparison calculator = new FundamentalFinancialComparison();

    @Test
    void freezesTheOriginalFactsAndCalculatesSignedChangesAcrossNegativeValues() {
        var baseline = baseline("2025-09-30", "-30");
        var current = baseline("2026-09-30", "20");
        var result = calculator.compare(baseline, current);
        assertEquals(FundamentalComparisonState.UPDATED, result.getState());
        assertEquals(new BigDecimal("50"), result.getChanges().get(0).getChange());
        assertEquals(new BigDecimal("-30"), baseline.getMetrics().get(0).getValue());
        assertEquals(64, result.getToken().length());
        current.getMetrics().get(0).setValue(new BigDecimal("21"));
        assertNotEquals(result.getToken(), calculator.compare(baseline, current).getToken());
    }

    @Test
    void cannotCompareDifferentCumulativePeriodsSourcesOrCurrencies() {
        var baseline = baseline("2025-09-30", "10");
        var current = baseline("2026-12-31", "20");
        assertEquals(FundamentalComparisonState.INCOMPARABLE, calculator.compare(baseline, current).getState());
        current.setPeriodEnd(LocalDate.parse("2026-09-30"));
        current.setCurrency("USD");
        assertEquals(FundamentalComparisonState.INCOMPARABLE, calculator.compare(baseline, current).getState());
        current.setCurrency("CNY");
        current.setSourceCode("OTHER");
        assertEquals(FundamentalComparisonState.INCOMPARABLE, calculator.compare(baseline, current).getState());
    }

    @Test
    void missingMetricsAreNotZerosAndLegacyRecordsAreNotReconstructedFromText() {
        var baseline = baseline("2025-09-30", "10");
        var current = baseline("2026-09-30", "20");
        current.setMetrics(List.of());
        var result = calculator.compare(baseline, current);
        assertEquals(FundamentalComparisonState.INCOMPARABLE, result.getState());
        assertTrue(result.getChanges().isEmpty());
        assertNull(result.getToken());
        assertEquals(FundamentalComparisonState.LEGACY, calculator.compare(null, current).getState());
        assertEquals(FundamentalComparisonState.WAITING, calculator.compare(baseline, baseline).getState());
    }

    private FundamentalFinancialBaseline baseline(String date, String cash) {
        var baseline = new FundamentalFinancialBaseline();
        baseline.setReportId(1L);
        baseline.setPeriodEnd(LocalDate.parse(date));
        baseline.setReportType("Q3");
        baseline.setScope("CONSOLIDATED");
        baseline.setCurrency("CNY");
        baseline.setSourceCode("SOURCE");
        baseline.setDimension(FundamentalDimension.EARNINGS_QUALITY);
        var metric = new FundamentalMetric();
        metric.setConceptCode("OPERATING_CASH_FLOW");
        metric.setLabel("经营现金流");
        metric.setUnit("CNY");
        metric.setValue(new BigDecimal(cash));
        baseline.setMetrics(List.of(metric));
        return baseline;
    }
}
