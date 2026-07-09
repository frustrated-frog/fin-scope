package com.finscope.domain.intake;

import java.time.LocalDateTime;

public class FetchBatch {
    private Long id;
    private Long sourceId;
    private String sourceName;
    private String triggerType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private int lookbackDays = 3;
    private int maxItemsRequested = 10;
    private int rawItemCount;
    private int candidateCount;
    private int agentReviewedCount;
    private int duplicateCount;
    private int lowValueCount;
    private String errorMessage;
    private String batchSummaryJson;
    private String batchSummaryText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public int getLookbackDays() {
        return lookbackDays;
    }

    public void setLookbackDays(int lookbackDays) {
        this.lookbackDays = lookbackDays;
    }

    public int getMaxItemsRequested() {
        return maxItemsRequested;
    }

    public void setMaxItemsRequested(int maxItemsRequested) {
        this.maxItemsRequested = maxItemsRequested;
    }

    public int getRawItemCount() {
        return rawItemCount;
    }

    public void setRawItemCount(int rawItemCount) {
        this.rawItemCount = rawItemCount;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public void setCandidateCount(int candidateCount) {
        this.candidateCount = candidateCount;
    }

    public int getAgentReviewedCount() {
        return agentReviewedCount;
    }

    public void setAgentReviewedCount(int agentReviewedCount) {
        this.agentReviewedCount = agentReviewedCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(int duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    public int getLowValueCount() {
        return lowValueCount;
    }

    public void setLowValueCount(int lowValueCount) {
        this.lowValueCount = lowValueCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getBatchSummaryJson() {
        return batchSummaryJson;
    }

    public void setBatchSummaryJson(String batchSummaryJson) {
        this.batchSummaryJson = batchSummaryJson;
    }

    public String getBatchSummaryText() {
        return batchSummaryText;
    }

    public void setBatchSummaryText(String batchSummaryText) {
        this.batchSummaryText = batchSummaryText;
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
}
