package com.finscope.domain.learningcard;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class StockLearningCardRun {
    private Long id;
    private Long cardId;
    private Long researchRunId;
    private String frameworkCode;
    private String status;
    private String stage;
    private String failedStage;
    private String errorCode;
    private String userMessage;
    private boolean retryable;
    private String conclusionStatus;
    private String summary;
    private String evidenceCompleteness;
    private String warningMessage;
    private String sourceFingerprint;
    private String generationMode;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private List<StockLearningCardClaim> claims = new ArrayList<StockLearningCardClaim>();
    private List<StockLearningCardEvidence> evidence = new ArrayList<StockLearningCardEvidence>();
    private List<StockLearningCardWatchItem> watchItems = new ArrayList<StockLearningCardWatchItem>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCardId() { return cardId; }
    public void setCardId(Long cardId) { this.cardId = cardId; }
    public Long getResearchRunId() { return researchRunId; }
    public void setResearchRunId(Long researchRunId) { this.researchRunId = researchRunId; }
    public String getFrameworkCode() { return frameworkCode; }
    public void setFrameworkCode(String frameworkCode) { this.frameworkCode = frameworkCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getFailedStage() { return failedStage; }
    public void setFailedStage(String failedStage) { this.failedStage = failedStage; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }
    public boolean isRetryable() { return retryable; }
    public void setRetryable(boolean retryable) { this.retryable = retryable; }
    public String getConclusionStatus() { return conclusionStatus; }
    public void setConclusionStatus(String conclusionStatus) { this.conclusionStatus = conclusionStatus; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getEvidenceCompleteness() { return evidenceCompleteness; }
    public void setEvidenceCompleteness(String evidenceCompleteness) { this.evidenceCompleteness = evidenceCompleteness; }
    public String getWarningMessage() { return warningMessage; }
    public void setWarningMessage(String warningMessage) { this.warningMessage = warningMessage; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public void setSourceFingerprint(String sourceFingerprint) { this.sourceFingerprint = sourceFingerprint; }
    public String getGenerationMode() { return generationMode; }
    public void setGenerationMode(String generationMode) { this.generationMode = generationMode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public List<StockLearningCardClaim> getClaims() { return claims; }
    public void setClaims(List<StockLearningCardClaim> claims) { this.claims = claims == null ? new ArrayList<StockLearningCardClaim>() : claims; }
    public List<StockLearningCardEvidence> getEvidence() { return evidence; }
    public void setEvidence(List<StockLearningCardEvidence> evidence) { this.evidence = evidence == null ? new ArrayList<StockLearningCardEvidence>() : evidence; }
    public List<StockLearningCardWatchItem> getWatchItems() { return watchItems; }
    public void setWatchItems(List<StockLearningCardWatchItem> watchItems) { this.watchItems = watchItems == null ? new ArrayList<StockLearningCardWatchItem>() : watchItems; }
}
