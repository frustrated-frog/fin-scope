package com.finscope.service.quant.forecast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class SingleStockWalkForwardValidator {
    private static final int RETRAIN_EVERY = 20;
    private static final int MINIMUM_TRAINING_SAMPLES = 120;

    WalkForwardResult validate(List<ForecastSample> input) {
        List<ForecastSample> samples = new ArrayList<ForecastSample>(input);
        samples.sort(Comparator.comparing(ForecastSample::getSignalDate));
        int initialTrainingSize = Math.max(MINIMUM_TRAINING_SAMPLES, (int) Math.floor(samples.size() * 0.60d));
        List<WalkForwardObservation> observations = new ArrayList<WalkForwardObservation>();
        RegularizedLogisticModel model = null;
        for (ForecastSample candidate : samples) {
            List<ForecastSample> matured = maturedBefore(samples, candidate.getSignalDate());
            if (matured.size() < initialTrainingSize) continue;
            if (model == null || observations.size() % RETRAIN_EVERY == 0) {
                model = RegularizedLogisticModel.fit(matured);
            }
            double baseline = positiveRate(matured);
            observations.add(new WalkForwardObservation(
                    candidate.getSignalDate(), matured.get(matured.size() - 1).getExitDate(),
                    model.predict(candidate.getFeatures()), baseline, candidate.getNetReturn()));
        }
        return metrics(observations);
    }

    private List<ForecastSample> maturedBefore(List<ForecastSample> samples, LocalDate signalDate) {
        List<ForecastSample> result = new ArrayList<ForecastSample>();
        for (ForecastSample sample : samples) {
            if (sample.getExitDate().isBefore(signalDate)) result.add(sample);
            else if (!sample.getSignalDate().isBefore(signalDate)) break;
        }
        return result;
    }

    private double positiveRate(List<ForecastSample> samples) {
        int positive = 0;
        for (ForecastSample sample : samples) if (sample.isPositive()) positive++;
        return positive / (double) samples.size();
    }

    private WalkForwardResult metrics(List<WalkForwardObservation> observations) {
        if (observations.isEmpty()) return new WalkForwardResult(Collections.<WalkForwardObservation>emptyList(), 0, 0d, 0d, 0d);
        int count = 0;
        int correct = 0;
        double brier = 0d;
        double baselineBrier = 0d;
        for (int i = 0; i < observations.size(); i += RETRAIN_EVERY) {
            WalkForwardObservation observation = observations.get(i);
            double actual = observation.isActualPositive() ? 1d : 0d;
            if (observation.isCorrect()) correct++;
            brier += squared(observation.getProbability() - actual);
            baselineBrier += squared(observation.getBaselineProbability() - actual);
            count++;
        }
        return new WalkForwardResult(observations, count, correct / (double) count,
                brier / count, baselineBrier / count);
    }

    private double squared(double value) { return value * value; }
}
