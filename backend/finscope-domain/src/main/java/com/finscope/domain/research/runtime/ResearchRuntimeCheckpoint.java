package com.finscope.domain.research.runtime;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ResearchRuntimeCheckpoint {
    private static final Set<String> TERMINAL_STATUSES = new HashSet<String>(Arrays.asList(
            "COMPLETED", "TERMINATED", "CANCELLED"));

    private Long researchRunId;
    private int stateVersion;
    private String phase;
    private String currentNode;
    private String status;
    private int iteration;
    private int consumedActions;
    private int maxActions;
    private int noProgressCount;
    private String lastStateHash;
    private int resumeCount;
    private String terminationReason;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isTerminal() {
        return status != null && TERMINAL_STATUSES.contains(status);
    }

    public Long getResearchRunId() { return researchRunId; }
    public void setResearchRunId(Long researchRunId) { this.researchRunId = researchRunId; }
    public int getStateVersion() { return stateVersion; }
    public void setStateVersion(int stateVersion) { this.stateVersion = stateVersion; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getCurrentNode() { return currentNode; }
    public void setCurrentNode(String currentNode) { this.currentNode = currentNode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getIteration() { return iteration; }
    public void setIteration(int iteration) { this.iteration = iteration; }
    public int getConsumedActions() { return consumedActions; }
    public void setConsumedActions(int consumedActions) { this.consumedActions = consumedActions; }
    public int getMaxActions() { return maxActions; }
    public void setMaxActions(int maxActions) { this.maxActions = maxActions; }
    public int getNoProgressCount() { return noProgressCount; }
    public void setNoProgressCount(int noProgressCount) { this.noProgressCount = noProgressCount; }
    public String getLastStateHash() { return lastStateHash; }
    public void setLastStateHash(String lastStateHash) { this.lastStateHash = lastStateHash; }
    public int getResumeCount() { return resumeCount; }
    public void setResumeCount(int resumeCount) { this.resumeCount = resumeCount; }
    public String getTerminationReason() { return terminationReason; }
    public void setTerminationReason(String terminationReason) { this.terminationReason = terminationReason; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
