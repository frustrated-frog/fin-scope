package com.finscope.domain.intake;

import java.time.LocalDateTime;

public class IntakeCandidate {
    private Long id;
    private Long batchId;
    private Long sourceId;
    private String sourceName;
    private String sourceType;
    private String originalTitle;
    private String originalUrl;
    private String originalSummary;
    private String originalBody;
    private String contentType;
    private String extractionMethod;
    private int extractionQualityScore;
    private LocalDateTime publishedAt;
    private LocalDateTime fetchedAt;
    private String chineseTitle;
    private String decisionSummary;
    private String keyFactsJson;
    private String whyItMatters;
    private String noveltyJudgment;
    private String riskFlagsJson;
    private int agentScore;
    private String agentRecommendation;
    private String agentReason;
    private String agentModel;
    private String agentStatus;
    private String agentErrorMessage;
    private String agentReviewJson;
    private String humanStatus;
    private String humanNote;
    private Long promotedArticleId;
    private LocalDateTime promotedAt;
    private Long duplicateOfCandidateId;
    private Long duplicateOfArticleId;
    private String urlFingerprint;
    private String titleFingerprint;
    private String bodyFingerprint;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getOriginalTitle() {
        return originalTitle;
    }

    public void setOriginalTitle(String originalTitle) {
        this.originalTitle = originalTitle;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getOriginalSummary() {
        return originalSummary;
    }

    public void setOriginalSummary(String originalSummary) {
        this.originalSummary = originalSummary;
    }

    public String getOriginalBody() {
        return originalBody;
    }

    public void setOriginalBody(String originalBody) {
        this.originalBody = originalBody;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getExtractionMethod() {
        return extractionMethod;
    }

    public void setExtractionMethod(String extractionMethod) {
        this.extractionMethod = extractionMethod;
    }

    public int getExtractionQualityScore() {
        return extractionQualityScore;
    }

    public void setExtractionQualityScore(int extractionQualityScore) {
        this.extractionQualityScore = extractionQualityScore;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public String getChineseTitle() {
        return chineseTitle;
    }

    public void setChineseTitle(String chineseTitle) {
        this.chineseTitle = chineseTitle;
    }

    public String getDecisionSummary() {
        return decisionSummary;
    }

    public void setDecisionSummary(String decisionSummary) {
        this.decisionSummary = decisionSummary;
    }

    public String getKeyFactsJson() {
        return keyFactsJson;
    }

    public void setKeyFactsJson(String keyFactsJson) {
        this.keyFactsJson = keyFactsJson;
    }

    public String getWhyItMatters() {
        return whyItMatters;
    }

    public void setWhyItMatters(String whyItMatters) {
        this.whyItMatters = whyItMatters;
    }

    public String getNoveltyJudgment() {
        return noveltyJudgment;
    }

    public void setNoveltyJudgment(String noveltyJudgment) {
        this.noveltyJudgment = noveltyJudgment;
    }

    public String getRiskFlagsJson() {
        return riskFlagsJson;
    }

    public void setRiskFlagsJson(String riskFlagsJson) {
        this.riskFlagsJson = riskFlagsJson;
    }

    public int getAgentScore() {
        return agentScore;
    }

    public void setAgentScore(int agentScore) {
        this.agentScore = agentScore;
    }

    public String getAgentRecommendation() {
        return agentRecommendation;
    }

    public void setAgentRecommendation(String agentRecommendation) {
        this.agentRecommendation = agentRecommendation;
    }

    public String getAgentReason() {
        return agentReason;
    }

    public void setAgentReason(String agentReason) {
        this.agentReason = agentReason;
    }

    public String getAgentModel() {
        return agentModel;
    }

    public void setAgentModel(String agentModel) {
        this.agentModel = agentModel;
    }

    public String getAgentStatus() {
        return agentStatus;
    }

    public void setAgentStatus(String agentStatus) {
        this.agentStatus = agentStatus;
    }

    public String getAgentErrorMessage() {
        return agentErrorMessage;
    }

    public void setAgentErrorMessage(String agentErrorMessage) {
        this.agentErrorMessage = agentErrorMessage;
    }

    public String getAgentReviewJson() {
        return agentReviewJson;
    }

    public void setAgentReviewJson(String agentReviewJson) {
        this.agentReviewJson = agentReviewJson;
    }

    public String getHumanStatus() {
        return humanStatus;
    }

    public void setHumanStatus(String humanStatus) {
        this.humanStatus = humanStatus;
    }

    public String getHumanNote() {
        return humanNote;
    }

    public void setHumanNote(String humanNote) {
        this.humanNote = humanNote;
    }

    public Long getPromotedArticleId() {
        return promotedArticleId;
    }

    public void setPromotedArticleId(Long promotedArticleId) {
        this.promotedArticleId = promotedArticleId;
    }

    public LocalDateTime getPromotedAt() {
        return promotedAt;
    }

    public void setPromotedAt(LocalDateTime promotedAt) {
        this.promotedAt = promotedAt;
    }

    public Long getDuplicateOfCandidateId() {
        return duplicateOfCandidateId;
    }

    public void setDuplicateOfCandidateId(Long duplicateOfCandidateId) {
        this.duplicateOfCandidateId = duplicateOfCandidateId;
    }

    public Long getDuplicateOfArticleId() {
        return duplicateOfArticleId;
    }

    public void setDuplicateOfArticleId(Long duplicateOfArticleId) {
        this.duplicateOfArticleId = duplicateOfArticleId;
    }

    public String getUrlFingerprint() {
        return urlFingerprint;
    }

    public void setUrlFingerprint(String urlFingerprint) {
        this.urlFingerprint = urlFingerprint;
    }

    public String getTitleFingerprint() {
        return titleFingerprint;
    }

    public void setTitleFingerprint(String titleFingerprint) {
        this.titleFingerprint = titleFingerprint;
    }

    public String getBodyFingerprint() {
        return bodyFingerprint;
    }

    public void setBodyFingerprint(String bodyFingerprint) {
        this.bodyFingerprint = bodyFingerprint;
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
