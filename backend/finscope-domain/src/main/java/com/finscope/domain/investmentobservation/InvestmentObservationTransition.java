package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.InvestmentObservationStage;

import java.time.LocalDateTime;

public class InvestmentObservationTransition {
    private Long id;
    private Long observationId;
    private InvestmentObservationStage fromStage;
    private InvestmentObservationStage toStage;
    private String reason;
    private String sourceFingerprint;
    private LocalDateTime occurredAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getObservationId() { return observationId; }
    public void setObservationId(Long observationId) { this.observationId = observationId; }
    public InvestmentObservationStage getFromStage() { return fromStage; }
    public void setFromStage(InvestmentObservationStage fromStage) { this.fromStage = fromStage; }
    public InvestmentObservationStage getToStage() { return toStage; }
    public void setToStage(InvestmentObservationStage toStage) { this.toStage = toStage; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public void setSourceFingerprint(String sourceFingerprint) { this.sourceFingerprint = sourceFingerprint; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
