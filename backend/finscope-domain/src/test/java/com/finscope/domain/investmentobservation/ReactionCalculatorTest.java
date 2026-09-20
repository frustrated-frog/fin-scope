package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionPathType;
import com.finscope.common.enums.investmentobservation.ReactionWindowStatus;
import com.finscope.domain.quant.data.QuantDailyBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReactionCalculatorTest {
    private final ReactionCalculator calculator = new ReactionCalculator();
    private final List<LocalDate> dates = List.of("2026-09-10", "2026-09-11", "2026-09-14", "2026-09-15",
            "2026-09-16", "2026-09-17", "2026-09-18", "2026-09-21", "2026-09-22", "2026-09-23", "2026-09-24")
            .stream().map(LocalDate::parse).toList();
    private final LocalDateTime published = LocalDateTime.parse("2026-09-18T10:00:00");

    @Test
    void computesCumulativeReturnsAgainstOneBaselineAndDescribesGiveback() {
        List<QuantDailyBar> stock = bars(100);
        stock.get(6).setAdjustedClose(new BigDecimal("106"));
        stock.get(10).setAdjustedClose(new BigDecimal("101"));
        List<QuantDailyBar> benchmark = bars(100);
        benchmark.get(6).setAdjustedClose(new BigDecimal("102"));
        ReactionCalculation result = calculate(stock, benchmark, "2026-09-24T16:00:00");
        assertEquals(new BigDecimal("4.0000"), result.getWindows().get(0).getRelativeReturnPp());
        assertEquals(new BigDecimal("6.0000"), result.getWindows().get(0).getStockReturnPct());
        assertEquals(new BigDecimal("1.0000"), result.getWindows().get(2).getRelativeReturnPp());
        assertEquals(ReactionPathType.GIVEBACK, result.getPathType());
        assertEquals(dates.get(5), result.getBaselineDate());
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void excludesUnclosedAndImmatureWindowsEvenIfProviderContainsFutureBars() {
        ReactionCalculation duringMarket = calculate(bars(100), bars(100), "2026-09-18T14:59:00");
        assertEquals(ReactionWindowStatus.NOT_DUE, duringMarket.getWindows().get(0).getStatus());
        assertNull(duringMarket.getWindows().get(0).getStockReturnPct());
        ReactionCalculation afterClose = calculate(bars(100), bars(100), "2026-09-18T15:30:00");
        assertEquals(ReactionWindowStatus.READY, afterClose.getWindows().get(0).getStatus());
        assertEquals(ReactionWindowStatus.NOT_DUE, afterClose.getWindows().get(1).getStatus());
        assertEquals(ReactionPathType.OBSERVING, afterClose.getPathType());
    }

    @Test
    void missingBaselineOrIntermediateBarDoesNotBecomeZeroOrShiftWindow() {
        List<QuantDailyBar> stock = bars(100);
        stock.remove(7);
        ReactionCalculation result = calculate(stock, bars(100), "2026-09-24T16:00:00");
        assertEquals(ReactionWindowStatus.READY, result.getWindows().get(0).getStatus());
        assertEquals(ReactionWindowStatus.MISSING_DATA, result.getWindows().get(1).getStatus());
        assertEquals(dates.get(8), result.getWindows().get(1).getEndDate());
        assertNull(result.getWindows().get(1).getRelativeReturnPp());
        List<QuantDailyBar> benchmark = bars(100);
        benchmark.remove(5);
        assertEquals(ReactionWindowStatus.MISSING_DATA,
                calculate(bars(100), benchmark, "2026-09-24T16:00:00").getWindows().get(0).getStatus());
    }

    @Test
    void zeroVolumeIsExplicitAndDuplicatedDatesAreRejected() {
        List<QuantDailyBar> stock = bars(100);
        stock.get(6).setVolume(BigDecimal.ZERO);
        ReactionCalculation result = calculate(stock, bars(100), "2026-09-24T16:00:00");
        assertEquals(ReactionWindowStatus.SUSPENDED, result.getWindows().get(0).getStatus());
        assertNull(result.getWindows().get(2).getRelativeReturnPp());
        stock.add(stock.get(0));
        assertThrows(IllegalArgumentException.class, () -> calculate(stock, bars(100), "2026-09-24T16:00:00"));
    }

    @Test
    void distinguishesPersistentDelayedAndWeakPathsOnlyAfterFiveSessions() {
        List<QuantDailyBar> stock = bars(100);
        stock.get(6).setAdjustedClose(new BigDecimal("103"));
        stock.get(10).setAdjustedClose(new BigDecimal("104"));
        assertEquals(ReactionPathType.PERSISTENT_STRENGTH, calculate(stock, bars(100), "2026-09-24T16:00:00").getPathType());
        stock.get(6).setAdjustedClose(new BigDecimal("100.5"));
        assertEquals(ReactionPathType.DELAYED_STRENGTH, calculate(stock, bars(100), "2026-09-24T16:00:00").getPathType());
        stock.get(10).setAdjustedClose(new BigDecimal("97"));
        assertEquals(ReactionPathType.RELATIVE_WEAKNESS, calculate(stock, bars(100), "2026-09-24T16:00:00").getPathType());
        assertEquals(ReactionPathType.NO_CLEAR_PATTERN, calculate(bars(100), bars(100), "2026-09-24T16:00:00").getPathType());
    }

    private ReactionCalculation calculate(List<QuantDailyBar> stock, List<QuantDailyBar> benchmark, String now) {
        return calculator.calculate(published, dates, stock, benchmark, LocalDateTime.parse(now));
    }

    private List<QuantDailyBar> bars(int close) {
        List<QuantDailyBar> result = new ArrayList<>();
        for (LocalDate date : dates) {
            QuantDailyBar bar = new QuantDailyBar();
            bar.setTradeDate(date);
            bar.setAdjustedClose(BigDecimal.valueOf(close));
            bar.setVolume(BigDecimal.TEN);
            result.add(bar);
        }
        return result;
    }
}
