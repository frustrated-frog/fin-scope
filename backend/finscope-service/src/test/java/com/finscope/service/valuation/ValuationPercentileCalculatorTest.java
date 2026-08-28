package com.finscope.service.valuation;

import com.finscope.domain.valuation.StockValuationSnapshot;
import com.finscope.domain.valuation.ValuationMetricSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ValuationPercentileCalculatorTest {
    private final ValuationPercentileCalculator calculator = new ValuationPercentileCalculator();

    @Test
    void computesMetricSpecificPercentileAcrossThreeAndFiveYearWindows() {
        List<StockValuationSnapshot> history = new ArrayList<StockValuationSnapshot>();
        LocalDate latestDate = LocalDate.of(2026, 8, 29);
        for (int index = 1; index <= 24; index++) {
            StockValuationSnapshot value = snapshot(latestDate.minusMonths(index), index, index * 2);
            history.add(value);
        }
        StockValuationSnapshot latest = snapshot(latestDate, 12, 30);
        history.add(latest);

        ValuationMetricSummary pe = calculator.summarize("PE_TTM", latest.getPeTtm(),
                latestDate, history, StockValuationSnapshot::getPeTtm);

        assertEquals(25, pe.getSampleCount3y());
        assertEquals(new BigDecimal("52.00"), pe.getPercentile3y());
        assertEquals(new BigDecimal("52.00"), pe.getPercentile5y());
    }

    @Test
    void keepsPercentileEmptyUntilEnoughPositiveSamplesExist() {
        LocalDate latestDate = LocalDate.of(2026, 8, 29);
        List<StockValuationSnapshot> history = List.of(
                snapshot(latestDate, 8, 4),
                snapshot(latestDate.minusDays(1), -1, 3));

        ValuationMetricSummary pe = calculator.summarize("PE_TTM", new BigDecimal("8"),
                latestDate, history, StockValuationSnapshot::getPeTtm);

        assertEquals(1, pe.getSampleCount3y());
        assertNull(pe.getPercentile3y());
        assertEquals("ACCUMULATING", pe.getHistoryStatus());
    }

    private static StockValuationSnapshot snapshot(LocalDate date, int pe, int pb) {
        StockValuationSnapshot value = new StockValuationSnapshot();
        value.setObservedDate(date);
        value.setPeTtm(new BigDecimal(pe));
        value.setPbMrq(new BigDecimal(pb));
        return value;
    }
}
