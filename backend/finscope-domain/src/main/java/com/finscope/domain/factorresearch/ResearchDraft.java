package com.finscope.domain.factorresearch;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A persisted research hand-off. It records context and evidence, but never
 * implies that an evaluation or strategy experiment has been executed.
 */
public class ResearchDraft {
    private Long id;
    private String sourceType;
    private String instrumentCode;
    private String instrumentName;
    private LocalDateTime observedAt;
    private String signalCode;
    private FactorIdentity factor;
    private Long snapshotId;
    private String snapshotFingerprint;
    private List<String> evidenceRefs = Collections.emptyList();
    private List<String> objectiveTags = Collections.emptyList();
    private String evaluationMode;
    private String status;
    private List<String> requiredNextSteps = Collections.emptyList();
    private LocalDateTime createdAt;

    public void validate() {
        required(sourceType, "sourceType");
        required(instrumentCode, "instrumentCode");
        required(instrumentName, "instrumentName");
        required(observedAt, "observedAt");
        required(signalCode, "signalCode");
        required(factor, "factor");
        required(snapshotId, "snapshotId");
        required(snapshotFingerprint, "snapshotFingerprint");
        required(evaluationMode, "evaluationMode");
        required(status, "status");
        required(createdAt, "createdAt");
        if (evidenceRefs.isEmpty()) {
            throw new IllegalArgumentException("evidenceRefs is required");
        }
        if (requiredNextSteps.isEmpty()) {
            throw new IllegalArgumentException("requiredNextSteps is required");
        }
    }

    private static <T> T required(T value, String field) {
        if (value == null || value instanceof String && ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static List<String> copy(List<String> values) {
        if (values == null) return Collections.emptyList();
        List<String> copy = new ArrayList<String>();
        for (String value : values) copy.add(required(value, "list item"));
        return Collections.unmodifiableList(copy);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getInstrumentCode() { return instrumentCode; }
    public void setInstrumentCode(String instrumentCode) { this.instrumentCode = instrumentCode; }
    public String getInstrumentName() { return instrumentName; }
    public void setInstrumentName(String instrumentName) { this.instrumentName = instrumentName; }
    public LocalDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(LocalDateTime observedAt) { this.observedAt = observedAt; }
    public String getSignalCode() { return signalCode; }
    public void setSignalCode(String signalCode) { this.signalCode = signalCode; }
    public FactorIdentity getFactor() { return factor; }
    public void setFactor(FactorIdentity factor) { this.factor = factor; }
    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public String getSnapshotFingerprint() { return snapshotFingerprint; }
    public void setSnapshotFingerprint(String snapshotFingerprint) { this.snapshotFingerprint = snapshotFingerprint; }
    public List<String> getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(List<String> evidenceRefs) { this.evidenceRefs = copy(evidenceRefs); }
    public List<String> getObjectiveTags() { return objectiveTags; }
    public void setObjectiveTags(List<String> objectiveTags) { this.objectiveTags = copy(objectiveTags); }
    public String getEvaluationMode() { return evaluationMode; }
    public void setEvaluationMode(String evaluationMode) { this.evaluationMode = evaluationMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getRequiredNextSteps() { return requiredNextSteps; }
    public void setRequiredNextSteps(List<String> requiredNextSteps) { this.requiredNextSteps = copy(requiredNextSteps); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
