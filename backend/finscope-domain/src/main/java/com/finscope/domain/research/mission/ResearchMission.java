package com.finscope.domain.research.mission;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchMission {
    private Long researchRunId;
    private String goal;
    private String subject;
    private String scopeSummary;
    private String researchType;
    private List<String> methodCodes = Collections.emptyList();
    private List<String> requiredEvidence = Collections.emptyList();
    private List<String> requiredCalculations = Collections.emptyList();
    private List<String> counterChecks = Collections.emptyList();
    private List<String> completionCriteria = Collections.emptyList();
    private List<String> successCriteria = Collections.emptyList();
    private String status;
    private String planningMode;
    private int planVersion;
    private int maxActions;
    private String activeTaskKey;
    private String fallbackReason;
    private String fallbackDetail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getResearchRunId() {
        return researchRunId;
    }

    public void setResearchRunId(Long researchRunId) {
        this.researchRunId = researchRunId;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getScopeSummary() {
        return scopeSummary;
    }

    public void setScopeSummary(String scopeSummary) {
        this.scopeSummary = scopeSummary;
    }

    public String getResearchType() { return researchType; }
    public void setResearchType(String researchType) { this.researchType = researchType; }
    public List<String> getMethodCodes() { return methodCodes; }
    public void setMethodCodes(List<String> values) { this.methodCodes = immutable(values); }
    public List<String> getRequiredEvidence() { return requiredEvidence; }
    public void setRequiredEvidence(List<String> values) { this.requiredEvidence = immutable(values); }
    public List<String> getRequiredCalculations() { return requiredCalculations; }
    public void setRequiredCalculations(List<String> values) { this.requiredCalculations = immutable(values); }
    public List<String> getCounterChecks() { return counterChecks; }
    public void setCounterChecks(List<String> values) { this.counterChecks = immutable(values); }
    public List<String> getCompletionCriteria() { return completionCriteria; }
    public void setCompletionCriteria(List<String> values) { this.completionCriteria = immutable(values); }

    public List<String> getSuccessCriteria() {
        return successCriteria;
    }

    public void setSuccessCriteria(List<String> successCriteria) {
        this.successCriteria = immutable(successCriteria);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPlanningMode() {
        return planningMode;
    }

    public void setPlanningMode(String planningMode) {
        this.planningMode = planningMode;
    }

    public int getPlanVersion() {
        return planVersion;
    }

    public void setPlanVersion(int planVersion) {
        this.planVersion = planVersion;
    }

    public int getMaxActions() {
        return maxActions;
    }

    public void setMaxActions(int maxActions) {
        this.maxActions = maxActions;
    }

    public String getActiveTaskKey() {
        return activeTaskKey;
    }

    public void setActiveTaskKey(String activeTaskKey) {
        this.activeTaskKey = activeTaskKey;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public String getFallbackDetail() {
        return fallbackDetail;
    }

    public void setFallbackDetail(String fallbackDetail) {
        this.fallbackDetail = fallbackDetail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    private static List<String> immutable(List<String> values) {
        return values == null || values.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
