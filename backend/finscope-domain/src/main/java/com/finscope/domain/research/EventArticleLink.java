package com.finscope.domain.research;

import java.time.LocalDateTime;

public class EventArticleLink {
    private Long eventId;
    private Long articleId;
    private String articleTitle;
    private String articleUrl;
    private String relationType;
    private Double matchScore;
    private String noveltyType;
    private String noveltyReason;
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
