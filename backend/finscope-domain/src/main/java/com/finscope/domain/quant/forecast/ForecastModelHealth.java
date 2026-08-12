package com.finscope.domain.quant.forecast;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ForecastModelHealth {
    private String instrumentCode;
    private int horizonDays;
    private String modelVersion;
    private String status;
    private boolean directionOutputPaused;
    private int sampleCount;
    private int coveredCount;
    private int abstainedCount;
    private double coverage;
    private double coveredAccuracy;
    private double brierScore;
    private double baselineBrierScore;
    private double logLoss;
    private double observedUpRate;
    private LocalDate firstAsOfDate;
    private LocalDate lastAsOfDate;
    private String conclusion;
}
