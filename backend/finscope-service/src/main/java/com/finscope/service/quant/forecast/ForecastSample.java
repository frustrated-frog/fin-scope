package com.finscope.service.quant.forecast;

import java.time.LocalDate;

final class ForecastSample {
    private final LocalDate signalDate;
    private final LocalDate entryDate;
    private final LocalDate exitDate;
    private final double[] features;
    private final double netReturn;

    ForecastSample(LocalDate signalDate, LocalDate entryDate, LocalDate exitDate,
                   double[] features, double netReturn) {
        this.signalDate = signalDate;
        this.entryDate = entryDate;
        this.exitDate = exitDate;
        this.features = features.clone();
        this.netReturn = netReturn;
    }

    LocalDate getSignalDate() { return signalDate; }
    LocalDate getEntryDate() { return entryDate; }
    LocalDate getExitDate() { return exitDate; }
    double[] getFeatures() { return features.clone(); }
    double getNetReturn() { return netReturn; }
    boolean isPositive() { return netReturn > 0d; }
}
