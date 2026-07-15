package com.finscope.domain.factorresearch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 基于冻结数据集计算出的不可变因子观测值。
 */
public final class FactorObservation {
    private final String datasetId;
    private final String instrumentCode;
    private final LocalDate tradeDate;
    private final LocalDateTime availableAt;
    private final FactorIdentity identity;
    private final BigDecimal rawValue;
    private final BigDecimal processedValue;
    private final String qualityStatus;
    private final String sourceFingerprint;
    private final String calculationFingerprint;

    public FactorObservation(String datasetId, String instrumentCode, LocalDate tradeDate,
                             LocalDateTime availableAt, FactorIdentity identity,
                             BigDecimal rawValue, BigDecimal processedValue, String qualityStatus,
                             String sourceFingerprint, String calculationFingerprint) {
        this.datasetId = required(datasetId, "datasetId");
        this.instrumentCode = required(instrumentCode, "instrumentCode");
        this.tradeDate = required(tradeDate, "tradeDate");
        this.availableAt = required(availableAt, "availableAt");
        this.identity = required(identity, "identity");
        this.qualityStatus = required(qualityStatus, "qualityStatus");
        this.sourceFingerprint = required(sourceFingerprint, "sourceFingerprint");
        this.calculationFingerprint = required(calculationFingerprint, "calculationFingerprint");
        if ("COMPLETE".equals(qualityStatus) && (rawValue == null || processedValue == null)) {
            throw new IllegalArgumentException("complete observation requires rawValue and processedValue");
        }
        this.rawValue = rawValue;
        this.processedValue = processedValue;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public String getInstrumentCode() {
        return instrumentCode;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public LocalDateTime getAvailableAt() {
        return availableAt;
    }

    public FactorIdentity getIdentity() {
        return identity;
    }

    public BigDecimal getRawValue() {
        return rawValue;
    }

    public BigDecimal getProcessedValue() {
        return processedValue;
    }

    public String getQualityStatus() {
        return qualityStatus;
    }

    public String getSourceFingerprint() {
        return sourceFingerprint;
    }

    public String getCalculationFingerprint() {
        return calculationFingerprint;
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
