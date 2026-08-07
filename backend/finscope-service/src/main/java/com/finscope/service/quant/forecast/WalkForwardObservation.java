package com.finscope.service.quant.forecast;

import java.time.LocalDate;

final class WalkForwardObservation {
    private final LocalDate signalDate;
    private final LocalDate trainingThrough;
    private final double probability;
    private final double baselineProbability;
    private final double actualReturn;

    WalkForwardObservation(LocalDate signalDate, LocalDate trainingThrough, double probability,
                           double baselineProbability, double actualReturn) {
        this.signalDate = signalDate;
        this.trainingThrough = trainingThrough;
        this.probability = probability;
        this.baselineProbability = baselineProbability;
        this.actualReturn = actualReturn;
    }

    LocalDate getSignalDate() { return signalDate; }
    LocalDate getTrainingThrough() { return trainingThrough; }
    double getProbability() { return probability; }
    double getBaselineProbability() { return baselineProbability; }
    double getActualReturn() { return actualReturn; }
    boolean isActualPositive() { return actualReturn > 0d; }
    boolean isCorrect() { return (probability >= 0.5d) == isActualPositive(); }
}
