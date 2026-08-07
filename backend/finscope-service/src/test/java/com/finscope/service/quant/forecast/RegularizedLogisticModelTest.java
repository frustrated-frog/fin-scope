package com.finscope.service.quant.forecast;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegularizedLogisticModelTest {
    @Test
    void learnsDeterministicProbabilitiesFromTrainingWindowOnly() {
        List<ForecastSample> samples = new ArrayList<ForecastSample>();
        LocalDate first = LocalDate.of(2018, 1, 1);
        for (int i = 0; i < 240; i++) {
            double signal = (i % 12) - 5.5d;
            samples.add(sample(first.plusDays(i), signal, signal > 0d ? 0.04d : -0.03d));
        }

        RegularizedLogisticModel firstModel = RegularizedLogisticModel.fit(samples);
        RegularizedLogisticModel secondModel = RegularizedLogisticModel.fit(samples);
        double negative = firstModel.predict(new double[] {-3d, 0, 0, 0, 0, 0, 0});
        double positive = firstModel.predict(new double[] {3d, 0, 0, 0, 0, 0, 0});

        assertTrue(negative >= 0d && positive <= 1d);
        assertTrue(positive > negative);
        assertEquals(positive, secondModel.predict(new double[] {3d, 0, 0, 0, 0, 0, 0}), 0.000000001d);
    }

    private ForecastSample sample(LocalDate signalDate, double feature, double result) {
        return new ForecastSample(signalDate, signalDate.plusDays(1), signalDate.plusDays(20),
                new double[] {feature, 0, 0, 0, 0, 0, 0}, result);
    }
}
