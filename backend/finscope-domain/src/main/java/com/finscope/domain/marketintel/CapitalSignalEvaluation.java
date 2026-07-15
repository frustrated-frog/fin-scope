package com.finscope.domain.marketintel;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单一资金行为信号在固定前向周期上的历史事件统计。
 */
@Data
public class CapitalSignalEvaluation {
    private String signalType;
    private String signalLabel;
    private int horizonDays;
    private int sampleCount;
    private BigDecimal averageReturn;
    private BigDecimal medianReturn;
    private BigDecimal positiveRate;
    private BigDecimal averageMfe;
    private BigDecimal averageMae;
    private String stabilityStatus;
    private String evaluationStatus;
    private LocalDate lastEventDate;

    public static CapitalSignalEvaluation insufficient(String signalType, String signalLabel,
                                                        int horizonDays, int sampleCount,
                                                        LocalDate lastEventDate) {
        CapitalSignalEvaluation value = new CapitalSignalEvaluation();
        value.signalType = signalType;
        value.signalLabel = signalLabel;
        value.horizonDays = horizonDays;
        value.sampleCount = sampleCount;
        value.stabilityStatus = "INSUFFICIENT_SAMPLE";
        value.evaluationStatus = "UNTESTED";
        value.lastEventDate = lastEventDate;
        return value;
    }

    public String evaluationRef() {
        return "evaluation:" + CapitalBehaviorEvaluation.VERSION + ":" + signalType + ":"
                + horizonDays + "d";
    }

    public boolean eligibleForAgent() {
        return sampleCount >= 5 && ("EXPLORATORY".equals(evaluationStatus)
                || "VALIDATED".equals(evaluationStatus));
    }
}
