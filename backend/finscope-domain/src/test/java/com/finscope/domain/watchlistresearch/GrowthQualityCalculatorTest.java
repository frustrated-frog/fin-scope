package com.finscope.domain.watchlistresearch;

import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.watchlistresearch.ResearchSignalKind;
import com.finscope.domain.instrument.StockFinancialQuarter;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GrowthQualityCalculatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);

    @Test
    void comparesRollingFourQuarterGrowthAndFlagsCashAndMarginDivergence() {
        StockGrowthQuality result = analyze(quarters());
        assertEquals(20.0, result.getRevenueGrowthPct(), 1e-10);
        assertEquals(20.0, result.getProfitGrowthPct(), 1e-10);
        assertEquals(.25, result.getCashConversion(), 1e-10);
        assertEquals(-5.0, result.getGrossMarginChangePp(), 1e-10);
        WatchlistResearchSnapshot snapshot = new WatchlistResearchSnapshot();
        snapshot.getGrowthQuality().add(result);
        assertEquals(List.of(ResearchSignalKind.MARGIN_EROSION, ResearchSignalKind.CASH_LAG).stream().sorted().toList(),
                WatchlistResearchRules.findings(snapshot).stream().map(ResearchFinding::getKind).sorted().toList());
    }

    @Test
    void missingCashDoesNotBecomeZeroOrHideOtherValidMetrics() {
        List<StockFinancialQuarter> quarters = quarters();
        quarters.get(7).setOperatingCashFlow(null);
        StockGrowthQuality result = analyze(quarters);
        assertNull(result.getOperatingCashFlowTtm());
        assertNull(result.getCashConversion());
        assertNotNull(result.getRevenueGrowthPct());
    }

    @Test
    void refusesCrossCurrencySourceAndDiscontinuousComparisons() {
        List<StockFinancialQuarter> quarters = quarters();
        quarters.get(0).setCurrency("USD");
        assertNull(analyze(quarters).getRevenueGrowthPct());
        assertNotNull(analyze(quarters).getRevenueTtm());
        quarters.get(0).setCurrency("CNY");
        quarters.get(7).setSourceCode("OTHER");
        assertNull(analyze(quarters).getRevenueTtm());
        quarters.get(7).setSourceCode("REPORTS");
        quarters.remove(5);
        assertNull(analyze(quarters).getRevenueTtm());
    }

    @Test
    void negativeProfitBaseHasNoGrowthPercentAndNegativeCurrentProfitHasNoCashRatio() {
        List<StockFinancialQuarter> quarters = quarters();
        quarters.get(0).setNetProfitParent(new BigDecimal("-100"));
        assertNull(analyze(quarters).getProfitGrowthPct());
        quarters.get(7).setNetProfitParent(new BigDecimal("-100"));
        assertNull(analyze(quarters).getCashConversion());
    }

    @Test
    void futureAndUnverifiedReportsCannotGenerateFinancialFindings() {
        List<StockFinancialQuarter> quarters = quarters();
        quarters.get(7).setQualityStatus(FinancialQualityStatus.UNVERIFIED);
        assertNull(analyze(quarters).getProfitTtm());
        assertNull(GrowthQualityCalculator.analyze(MarketResearchCalculatorTest.stock("a"), quarters,
                LocalDate.of(2025, 1, 1)).getProfitTtm());
    }

    @Test
    void marginIsRevenueWeightedRatherThanAverageOfQuarterPercentages() {
        List<StockFinancialQuarter> quarters = quarters();
        quarters.get(7).setRevenue(new BigDecimal("360"));
        quarters.get(7).setGrossMarginPct(new BigDecimal("10"));
        assertEquals(17.5, analyze(quarters).getGrossMarginPct(), 1e-10);
    }

    private StockGrowthQuality analyze(List<StockFinancialQuarter> quarters) {
        return GrowthQualityCalculator.analyze(MarketResearchCalculatorTest.stock("a"), quarters, TODAY);
    }

    private List<StockFinancialQuarter> quarters() {
        List<StockFinancialQuarter> result = new ArrayList<StockFinancialQuarter>();
        for (int index = 0; index < 8; index++) {
            StockFinancialQuarter quarter = new StockFinancialQuarter();
            quarter.setPeriodEnd(YearMonth.of(2024, 12).plusMonths(index * 3L).atEndOfMonth());
            quarter.setCurrency("CNY");
            quarter.setSourceCode("REPORTS");
            quarter.setQualityStatus(FinancialQualityStatus.FRESH);
            quarter.setRevenue(new BigDecimal(index < 4 ? "100" : "120"));
            quarter.setNetProfitParent(new BigDecimal(index < 4 ? "10" : "12"));
            quarter.setOperatingCashFlow(new BigDecimal("3"));
            quarter.setGrossMarginPct(new BigDecimal(index < 4 ? "30" : "25"));
            result.add(quarter);
        }
        return result;
    }
}
