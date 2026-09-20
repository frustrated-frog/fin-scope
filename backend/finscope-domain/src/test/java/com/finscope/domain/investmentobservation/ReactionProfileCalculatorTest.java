package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionWindowStatus;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class ReactionProfileCalculatorTest {
    @Test
    void distinguishesMidWindowSpikeAndRecoveryAndDescribesPartialWindow() {
        var spike = profile(0.5, 10, 7, 4, 3);
        assertEquals(2, spike.getPeakSession());
        assertEquals(0, new BigDecimal("7").compareTo(spike.getGivebackPp()));
        assertTrue(spike.getSummary().contains("随后回吐"));
        var recovery = profile(3, -12, -8, 0, 4);
        assertTrue(recovery.getSummary().contains("修复"));
        assertTrue(recovery.getMaxDrawdownPct().doubleValue() > 14);
        var partial = profile(3, 4, 5);
        assertFalse(partial.isWindowEnded());
        assertTrue(partial.getSummary().contains("第3个交易日"));
    }

    private ReactionProfile profile(double... values) {
        ReactionCalculation calculation = new ReactionCalculation();
        for (int i = 0; i < values.length; i++) {
            ReactionPoint point = new ReactionPoint();
            point.setSession(i + 1);
            point.setTradeDate(LocalDate.of(2026, 9, 1).plusDays(i));
            point.setStatus(ReactionWindowStatus.READY);
            point.setStockReturnPct(BigDecimal.valueOf(values[i]));
            point.setRelativeReturnPp(BigDecimal.valueOf(values[i]));
            calculation.getPoints().add(point);
        }
        return new ReactionProfileCalculator().calculate(calculation, LocalDate.of(2026, 9, values.length).atTime(16, 0));
    }
}
