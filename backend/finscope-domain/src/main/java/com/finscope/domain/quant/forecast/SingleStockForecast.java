package com.finscope.domain.quant.forecast;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class SingleStockForecast {
    private String instrumentCode;
    private LocalDate asOfDate;
    private int horizonDays;
    private String status;
    private String conclusion;
    private Integer barCount;
    private Integer labeledSampleCount;
    private Double upProbability;
    private Double expectedNetReturn;
    private Double lowerNetReturn;
    private Double upperNetReturn;
    private String dataFingerprint;
    private String sourceCode;
    private String sourceFamily;
    private String qualityStatus;
    private Validation validation;
    private List<Observation> recentObservations = new ArrayList<Observation>();
    private List<String> warnings = new ArrayList<String>();

    @Data
    public static class Validation {
        private int outOfSampleCount;
        private int independentSampleCount;
        private double accuracy;
        private double brierScore;
        private double baselineBrierScore;
        private double observedUpRate;
    }

    @Data
    public static class Observation {
        private LocalDate signalDate;
        private double probability;
        private double actualNetReturn;
        private boolean correct;
    }
}
