package com.finscope.domain.research;

import java.time.LocalDateTime;

public class EventArticleLink {
    /**
     * 事件 ID。
     */
    private Long eventId;
    /**
     * 文章 ID。
     */
    private Long articleId;
    /**
     * 文章标题。
     */
    private String articleTitle;
    /**
     * 文章 URL。
     */
    private String articleUrl;
    /**
     * 关联类型。
     */
    private String relationType;
    /**
     * 匹配分数。
     */
    private Double matchScore;
    /**
     * 新意类型。
     */
    private String noveltyType;
    /**
     * 新意判断原因。
     */
    private String noveltyReason;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

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

    public String getArticleTitle() {
        return articleTitle;
    }

    public void setArticleTitle(String articleTitle) {
        this.articleTitle = articleTitle;
    }

    public String getArticleUrl() {
        return articleUrl;
    }

    public void setArticleUrl(String articleUrl) {
        this.articleUrl = articleUrl;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public Double getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Double matchScore) {
        this.matchScore = matchScore;
    }

    public String getNoveltyType() {
        return noveltyType;
    }

    public void setNoveltyType(String noveltyType) {
        this.noveltyType = noveltyType;
    }

    public String getNoveltyReason() {
        return noveltyReason;
    }

    public void setNoveltyReason(String noveltyReason) {
        this.noveltyReason = noveltyReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
