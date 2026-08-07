package com.finscope.service.quant.forecast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class WalkForwardResult {
    private final List<WalkForwardObservation> observations;
    private final int independentSampleCount;
    private final double accuracy;
    private final double brierScore;
    private final double baselineBrierScore;

    WalkForwardResult(List<WalkForwardObservation> observations, int independentSampleCount,
                      double accuracy, double brierScore, double baselineBrierScore) {
        this.observations = Collections.unmodifiableList(new ArrayList<WalkForwardObservation>(observations));
        this.independentSampleCount = independentSampleCount;
        this.accuracy = accuracy;
        this.brierScore = brierScore;
        this.baselineBrierScore = baselineBrierScore;
    }

    List<WalkForwardObservation> getObservations() { return observations; }
    int getIndependentSampleCount() { return independentSampleCount; }
    double getAccuracy() { return accuracy; }
    double getBrierScore() { return brierScore; }
    double getBaselineBrierScore() { return baselineBrierScore; }
}
