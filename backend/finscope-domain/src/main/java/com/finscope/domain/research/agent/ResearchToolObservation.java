package com.finscope.domain.research.agent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchToolObservation {
    private Long id;
    private Long researchRunId;
    private Long decisionId;
    private String toolCode;
    private String status;
    private String observationSummary;
    private String newInformation;
    private int evidenceDelta;
    private int sourceDelta;
    private List<String> dataRefs = Collections.emptyList();
    private String errorType;
    private boolean retryable;
    private int attemptCount = 1;
    private String stateHash;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getResearchRunId() { return researchRunId; }
    public void setResearchRunId(Long researchRunId) { this.researchRunId = researchRunId; }
    public Long getDecisionId() { return decisionId; }
    public void setDecisionId(Long decisionId) { this.decisionId = decisionId; }
    public String getToolCode() { return toolCode; }
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getObservationSummary() { return observationSummary; }
    public void setObservationSummary(String observationSummary) { this.observationSummary = observationSummary; }
    public String getNewInformation() { return newInformation; }
    public void setNewInformation(String newInformation) { this.newInformation = newInformation; }
    public int getEvidenceDelta() { return evidenceDelta; }
    public void setEvidenceDelta(int evidenceDelta) { this.evidenceDelta = evidenceDelta; }
    public int getSourceDelta() { return sourceDelta; }
    public void setSourceDelta(int sourceDelta) { this.sourceDelta = sourceDelta; }
    public List<String> getDataRefs() { return dataRefs; }
    public void setDataRefs(List<String> dataRefs) {
        this.dataRefs = dataRefs == null || dataRefs.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(dataRefs));
    }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public boolean isRetryable() { return retryable; }
    public void setRetryable(boolean retryable) { this.retryable = retryable; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = Math.max(1, attemptCount); }
    public String getStateHash() { return stateHash; }
    public void setStateHash(String stateHash) { this.stateHash = stateHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
