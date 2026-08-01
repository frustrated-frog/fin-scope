package com.finscope.domain.research.agent;

import java.time.LocalDateTime;

public class ResearchAgentDecision {
    private Long id;
    private Long researchRunId;
    private int iteration;
    private String decisionType;
    private String currentSubgoal;
    private String missionTaskKey;
    private String toolCode;
    private String argumentsJson;
    private String targetGap;
    private String expectedObservation;
    private String decisionSummary;
    private double confidence;
    private String decisionMode;
    private String actionFingerprint;
    private String status;
    private String validationError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getResearchRunId() { return researchRunId; }
    public void setResearchRunId(Long researchRunId) { this.researchRunId = researchRunId; }
    public int getIteration() { return iteration; }
    public void setIteration(int iteration) { this.iteration = iteration; }
    public String getDecisionType() { return decisionType; }
    public void setDecisionType(String decisionType) { this.decisionType = decisionType; }
    public String getCurrentSubgoal() { return currentSubgoal; }
    public void setCurrentSubgoal(String currentSubgoal) { this.currentSubgoal = currentSubgoal; }
    public String getMissionTaskKey() { return missionTaskKey; }
    public void setMissionTaskKey(String missionTaskKey) { this.missionTaskKey = missionTaskKey; }
    public String getToolCode() { return toolCode; }
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
    public String getArgumentsJson() { return argumentsJson; }
    public void setArgumentsJson(String argumentsJson) { this.argumentsJson = argumentsJson; }
    public String getTargetGap() { return targetGap; }
    public void setTargetGap(String targetGap) { this.targetGap = targetGap; }
    public String getExpectedObservation() { return expectedObservation; }
    public void setExpectedObservation(String expectedObservation) { this.expectedObservation = expectedObservation; }
    public String getDecisionSummary() { return decisionSummary; }
    public void setDecisionSummary(String decisionSummary) { this.decisionSummary = decisionSummary; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getDecisionMode() { return decisionMode; }
    public void setDecisionMode(String decisionMode) { this.decisionMode = decisionMode; }
    public String getActionFingerprint() { return actionFingerprint; }
    public void setActionFingerprint(String actionFingerprint) { this.actionFingerprint = actionFingerprint; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getValidationError() { return validationError; }
    public void setValidationError(String validationError) { this.validationError = validationError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
