package com.finscope.service.financials;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuarterDerivationEngineTest {
    private final QuarterDerivationEngine engine = new QuarterDerivationEngine();

    @Test
    void subtractsPriorCumulativeValueWithoutLosingScale() {
        assertEquals(new BigDecimal("360000000.12"), engine.singleQuarter(
                new BigDecimal("960000000.24"),
                new BigDecimal("600000000.12")));
    }

    @Test
    void returnsNullWhenEitherCumulativeInputIsMissing() {
        assertNull(engine.singleQuarter(new BigDecimal("960000000"), null));
        assertNull(engine.singleQuarter(null, new BigDecimal("600000000")));
    }
}
