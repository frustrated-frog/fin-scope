package com.finscope.service.quant.forecast;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleStockWalkForwardValidatorTest {
    @Test
    void validatesInTimeOrderUsingOnlyMaturedLabels() {
        List<ForecastSample> samples = samples(500);

        WalkForwardResult result = new SingleStockWalkForwardValidator().validate(samples);

        assertFalse(result.getObservations().isEmpty());
        for (WalkForwardObservation observation : result.getObservations()) {
            assertTrue(observation.getTrainingThrough().isBefore(observation.getSignalDate()));
            assertTrue(observation.getProbability() >= 0d && observation.getProbability() <= 1d);
        }
        assertTrue(result.getIndependentSampleCount() > 5);
        assertTrue(result.getBrierScore() >= 0d);
        assertTrue(result.getBaselineBrierScore() >= 0d);
    }

    @Test
    void producesStableMetricsForIdenticalInput() {
        SingleStockWalkForwardValidator validator = new SingleStockWalkForwardValidator();

        WalkForwardResult first = validator.validate(samples(500));
        WalkForwardResult second = validator.validate(samples(500));

        assertEquals(first.getBrierScore(), second.getBrierScore(), 0.000000001d);
        assertEquals(first.getAccuracy(), second.getAccuracy(), 0.000000001d);
        assertEquals(first.getObservations().size(), second.getObservations().size());
    }

    private List<ForecastSample> samples(int count) {
        List<ForecastSample> values = new ArrayList<ForecastSample>();
        LocalDate first = LocalDate.of(2010, 1, 1);
        for (int i = 0; i < count; i++) {
            double cycle = Math.sin(i / 13d);
            double result = cycle * 0.05d + (i % 7 == 0 ? -0.01d : 0.005d);
            values.add(new ForecastSample(
                    first.plusDays(i), first.plusDays(i + 1), first.plusDays(i + 20),
                    new double[] {cycle, cycle / 2d, i % 20 / 20d, 0, 0, 0.2d, 0}, result));
        }
        return values;
    }
}
