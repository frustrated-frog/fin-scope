package com.finscope.service.quant.backtest;

import com.finscope.domain.quant.backtest.BacktestMetrics;
import com.finscope.domain.quant.backtest.EquityPoint;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceMetricsTest {
    @Test
    void calculatesCompoundedReturnAndDrawdownWithoutNonFiniteValues() {
        BacktestMetrics result = new PerformanceMetrics().calculate(Arrays.asList(
                point(1, 1), point(2, 1.10), point(3, 0.99), point(4, 1.20)), 0, 0);
        assertEquals(0.20, result.getTotalReturn(), 0.000001);
        assertEquals(0.10, result.getMaxDrawdown(), 0.000001);
        assertTrue(Double.isFinite(result.getAnnualizedReturn()));
        assertTrue(Double.isFinite(result.getSharpeRatio()));
    }

    private EquityPoint point(int day, double nav) {
        EquityPoint value = new EquityPoint(); value.setTradeDate(LocalDate.of(2024, 1, day)); value.setPortfolioNav(nav); return value;
    }
}
