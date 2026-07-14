package com.finscope.domain.research;

import java.time.LocalDateTime;

public class EvidenceItem {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 事件 ID。
     */
    private Long eventId;
    /**
     * 文章 ID。
     */
    private Long articleId;
    /**
     * 来源层级。
     */
    private String sourceTier;
    /**
     * 证据类型。
     */
    private String evidenceType;
    /**
     * 归因或证据主张。
     */
    private String claim;
    /**
     * 主张键。
     */
    private String claimKey;
    /**
     * 置信度。
     */
    private Integer confidence;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 文章标题。
     */
    private String articleTitle;
    /**
     * 文章 URL。
     */
    private String articleUrl;
    /**
     * 文章发布时间。
     */
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
