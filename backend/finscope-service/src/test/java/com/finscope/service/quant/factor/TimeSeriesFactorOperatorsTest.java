package com.finscope.service.quant.factor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TimeSeriesFactorOperatorsTest {
    private final TimeSeriesFactorOperators operators = new TimeSeriesFactorOperators();

    @Test
    void computesStableDescriptiveAndRegressionOperators() {
        assertDecimal("3.000000", operators.delta(values("1", "4")));
        assertDecimal("2.500000", operators.mean(values("1", "2", "3", "4")));
        assertDecimal("10.000000", operators.sum(values("1", "2", "3", "4")));
        assertDecimal("1.118034", operators.std(values("1", "2", "3", "4")));
        assertDecimal("1.341641", operators.zScore(values("1", "2", "3", "4"), decimal("4")));
        assertDecimal("0.750000", operators.tsRank(values("1", "2", "3", "4"), decimal("3")));
        assertDecimal("2.500000", operators.quantile(values("1", "2", "3", "4"), decimal("0.5")));
        assertDecimal("1.000000", operators.slope(values("1", "2", "3", "4")));
        assertDecimal("1.000000", operators.rSquare(values("1", "2", "3", "4")));
        assertDecimal("-1.000000", operators.correlation(values("1", "2", "3"), values("3", "2", "1")));
        assertDecimal("1.000000", operators.min(values("1", "2", "3")));
        assertDecimal("3.000000", operators.max(values("1", "2", "3")));
        assertDecimal("0.500000", operators.positiveShare(values("2", "-1", "0", "3")));
    }

    @Test
    void refusesToInventValuesForInsufficientOrDegenerateInput() {
        assertFalse(operators.mean(Collections.emptyList()).isPresent());
        assertFalse(operators.delta(Collections.singletonList(decimal("1"))).isPresent());
        assertFalse(operators.zScore(values("1", "1"), decimal("1")).isPresent());
        assertFalse(operators.correlation(values("1", "1"), values("2", "3")).isPresent());
        assertFalse(operators.quantile(values("1", "2"), decimal("1.1")).isPresent());
        assertFalse(operators.positiveShare(Arrays.asList(null, null)).isPresent());
    }

    private void assertDecimal(String expected, Optional<BigDecimal> actual) {
        assertEquals(decimal(expected), actual.orElseThrow(AssertionError::new));
    }

    private java.util.List<BigDecimal> values(String... values) {
        return Arrays.stream(values).map(this::decimal).collect(java.util.stream.Collectors.toList());
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
