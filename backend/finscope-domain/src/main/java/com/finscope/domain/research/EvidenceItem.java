package com.finscope.domain.research;

import java.time.LocalDateTime;

public class EvidenceItem {
    private Long id;
    private Long eventId;
    private Long articleId;
    private String sourceTier;
    private String evidenceType;
    private String claim;
    private String claimKey;
    private Integer confidence;
    private LocalDateTime createdAt;
    private String articleTitle;
    private String articleUrl;
    private LocalDateTime articlePublishedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public String getSourceTier() {
        return sourceTier;
    }

    public void setSourceTier(String sourceTier) {
        this.sourceTier = sourceTier;
    }

    public String getEvidenceType() {
        return evidenceType;
    }

    public void setEvidenceType(String evidenceType) {
        this.evidenceType = evidenceType;
    }

    public String getClaim() {
        return claim;
    }

    public void setClaim(String claim) {
        this.claim = claim;
    }

    public String getClaimKey() { return claimKey; }
    public void setClaimKey(String claimKey) { this.claimKey = claimKey; }

    public Integer getConfidence() {
        return confidence;
    }

    public void setConfidence(Integer confidence) {
        this.confidence = confidence;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getArticleTitle() { return articleTitle; }
    public void setArticleTitle(String articleTitle) { this.articleTitle = articleTitle; }
    public String getArticleUrl() { return articleUrl; }
    public void setArticleUrl(String articleUrl) { this.articleUrl = articleUrl; }
    public LocalDateTime getArticlePublishedAt() { return articlePublishedAt; }
    public void setArticlePublishedAt(LocalDateTime articlePublishedAt) { this.articlePublishedAt = articlePublishedAt; }
}
