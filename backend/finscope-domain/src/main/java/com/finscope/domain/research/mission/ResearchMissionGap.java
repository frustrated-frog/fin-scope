package com.finscope.domain.research.mission;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchMissionGap {
    private Long id;
    private Long researchRunId;
    private int assessmentIndex;
    private String afterTaskKey;
    private boolean sufficient;
    private int evidenceCount;
    private int sourceCount;
    private int supportCount;
    private int counterCount;
    private List<String> warnings = Collections.emptyList();
    private String recommendedIntent;
    private String stateHash;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getResearchRunId() {
        return researchRunId;
    }

    public void setResearchRunId(Long researchRunId) {
        this.researchRunId = researchRunId;
    }

    public int getAssessmentIndex() {
        return assessmentIndex;
    }

    public void setAssessmentIndex(int assessmentIndex) {
        this.assessmentIndex = assessmentIndex;
    }

    public String getAfterTaskKey() {
        return afterTaskKey;
    }

    public void setAfterTaskKey(String afterTaskKey) {
        this.afterTaskKey = afterTaskKey;
    }

    public boolean isSufficient() {
        return sufficient;
    }

    public void setSufficient(boolean sufficient) {
        this.sufficient = sufficient;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(int evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public int getSourceCount() {
        return sourceCount;
    }

    public void setSourceCount(int sourceCount) {
        this.sourceCount = sourceCount;
    }

    public int getSupportCount() {
        return supportCount;
    }

    public void setSupportCount(int supportCount) {
        this.supportCount = supportCount;
    }

    public int getCounterCount() {
        return counterCount;
    }

    public void setCounterCount(int counterCount) {
        this.counterCount = counterCount;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null || warnings.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public String getRecommendedIntent() {
        return recommendedIntent;
    }

    public void setRecommendedIntent(String recommendedIntent) {
        this.recommendedIntent = recommendedIntent;
    }

    public String getStateHash() {
        return stateHash;
    }

    public void setStateHash(String stateHash) {
        this.stateHash = stateHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
