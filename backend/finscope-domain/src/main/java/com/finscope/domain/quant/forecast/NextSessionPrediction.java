package com.finscope.domain.quant.forecast;

import com.finscope.common.enums.quant.NextSessionStatus;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Price prediction only: next exchange close relative to the frozen close, not executable P&L. */
@Data
public class NextSessionPrediction {
    private NextSessionStatus status;
    private LocalDate asOfDate;
    private LocalDate targetDate;
    private LocalDateTime generatedAt;
    private String label;
    private Double lastClose;
    private Double upProbability;
    private Double expectedReturn;
    private Double lowerReturn;
    private Double upperReturn;
    private String decision;
    private String modelCode;
    private String modelVersion;
    private String dataFingerprint;
    private LocalDate trainingThrough;
    private LocalDate calibrationThrough;
    private int trainingSampleCount;
    private int calibrationSampleCount;
    private int validationSampleCount;
    private Double accuracy;
    private Double brierScore;
    private Double baselineBrierScore;
    private Double intervalCoverage;
    private List<String> warnings = new ArrayList<>();
}
