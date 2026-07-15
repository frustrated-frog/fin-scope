package com.finscope.domain.factorresearch;

import com.finscope.domain.instrument.InstrumentCodeCanonicalizer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

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
    private final ObservationQuality qualityStatus;
    private final String sourceFingerprint;
    private final String calculationFingerprint;

    public FactorObservation(String datasetId, String instrumentCode, LocalDate tradeDate,
                             LocalDateTime availableAt, FactorIdentity identity,
                             BigDecimal rawValue, BigDecimal processedValue, ObservationQuality qualityStatus,
                             String sourceFingerprint, String calculationFingerprint) {
        this.datasetId = required(datasetId, "datasetId");
        this.instrumentCode = InstrumentCodeCanonicalizer.canonical(instrumentCode, null);
        this.tradeDate = required(tradeDate, "tradeDate");
        this.availableAt = required(availableAt, "availableAt");
        this.identity = required(identity, "identity");
        this.qualityStatus = required(qualityStatus, "qualityStatus");
        this.sourceFingerprint = required(sourceFingerprint, "sourceFingerprint");
        this.calculationFingerprint = required(calculationFingerprint, "calculationFingerprint");
        if (qualityStatus == ObservationQuality.COMPLETE && (rawValue == null || processedValue == null)) {
            throw new IllegalArgumentException("complete observation requires rawValue and processedValue");
        }
        if (qualityStatus != ObservationQuality.COMPLETE && (rawValue != null || processedValue != null)) {
            throw new IllegalArgumentException("incomplete observation cannot contain values");
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

    public ObservationQuality getQualityStatus() {
        return qualityStatus;
    }

    public String getSourceFingerprint() {
        return sourceFingerprint;
    }

    public String getCalculationFingerprint() {
        return calculationFingerprint;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof FactorObservation)) {
            return false;
        }
        FactorObservation that = (FactorObservation) value;
        return datasetId.equals(that.datasetId)
                && instrumentCode.equals(that.instrumentCode)
                && tradeDate.equals(that.tradeDate)
                && availableAt.equals(that.availableAt)
                && identity.equals(that.identity)
                && Objects.equals(rawValue, that.rawValue)
                && Objects.equals(processedValue, that.processedValue)
                && qualityStatus == that.qualityStatus
                && sourceFingerprint.equals(that.sourceFingerprint)
                && calculationFingerprint.equals(that.calculationFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datasetId, instrumentCode, tradeDate, availableAt, identity,
                rawValue, processedValue, qualityStatus, sourceFingerprint, calculationFingerprint);
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
