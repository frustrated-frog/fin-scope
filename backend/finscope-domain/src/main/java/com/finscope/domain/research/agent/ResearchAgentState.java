package com.finscope.domain.research.agent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchAgentState {
    private Long researchRunId;
    private String status;
    private int stateVersion;
    private String currentSubgoal;
    private String planSummary;
    private String memorySummary;
    private String evidenceSummary;
    private List<String> attemptedFingerprints = Collections.emptyList();
    private Long lastObservationId;
    private int decisionCount;
    private int replanCount;
    private int noProgressCount;
    private int finishRejectionCount;
    private int fallbackCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getResearchRunId() { return researchRunId; }
    public void setResearchRunId(Long researchRunId) { this.researchRunId = researchRunId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getStateVersion() { return stateVersion; }
    public void setStateVersion(int stateVersion) { this.stateVersion = stateVersion; }
    public String getCurrentSubgoal() { return currentSubgoal; }
    public void setCurrentSubgoal(String currentSubgoal) { this.currentSubgoal = currentSubgoal; }
    public String getPlanSummary() { return planSummary; }
    public void setPlanSummary(String planSummary) { this.planSummary = planSummary; }
    public String getMemorySummary() { return memorySummary; }
    public void setMemorySummary(String memorySummary) { this.memorySummary = memorySummary; }
    public String getEvidenceSummary() { return evidenceSummary; }
    public void setEvidenceSummary(String evidenceSummary) { this.evidenceSummary = evidenceSummary; }
    public List<String> getAttemptedFingerprints() { return attemptedFingerprints; }
    public void setAttemptedFingerprints(List<String> attemptedFingerprints) {
        this.attemptedFingerprints = attemptedFingerprints == null || attemptedFingerprints.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(attemptedFingerprints));
    }
    public Long getLastObservationId() { return lastObservationId; }
    public void setLastObservationId(Long lastObservationId) { this.lastObservationId = lastObservationId; }
    public int getDecisionCount() { return decisionCount; }
    public void setDecisionCount(int decisionCount) { this.decisionCount = decisionCount; }
    public int getReplanCount() { return replanCount; }
    public void setReplanCount(int replanCount) { this.replanCount = replanCount; }
    public int getNoProgressCount() { return noProgressCount; }
    public void setNoProgressCount(int noProgressCount) { this.noProgressCount = noProgressCount; }
    public int getFinishRejectionCount() { return finishRejectionCount; }
    public void setFinishRejectionCount(int finishRejectionCount) { this.finishRejectionCount = finishRejectionCount; }
    public int getFallbackCount() { return fallbackCount; }
    public void setFallbackCount(int fallbackCount) { this.fallbackCount = fallbackCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
