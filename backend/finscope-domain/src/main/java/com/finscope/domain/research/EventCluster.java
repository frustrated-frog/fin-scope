package com.finscope.domain.research;

import java.time.LocalDateTime;

public class EventCluster {
    private Long id;
    private String canonicalTitle;
    private String canonicalEventKey;
    private String themeCode;
    private String summary;
    private String status;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime lastMeaningfulUpdateAt;
    private Integer importanceScore;
    private String noveltyState;
    private Integer evidenceCount;
    private Integer articleCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCanonicalTitle() {
        return canonicalTitle;
    }

    public void setCanonicalTitle(String canonicalTitle) {
        this.canonicalTitle = canonicalTitle;
    }

    public String getCanonicalEventKey() {
        return canonicalEventKey;
    }

    public void setCanonicalEventKey(String canonicalEventKey) {
        this.canonicalEventKey = canonicalEventKey;
    }

    public String getThemeCode() {
        return themeCode;
    }

    public void setThemeCode(String themeCode) {
        this.themeCode = themeCode;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(LocalDateTime firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public LocalDateTime getLastMeaningfulUpdateAt() {
        return lastMeaningfulUpdateAt;
    }

    public void setLastMeaningfulUpdateAt(LocalDateTime lastMeaningfulUpdateAt) {
        this.lastMeaningfulUpdateAt = lastMeaningfulUpdateAt;
    }

    public Integer getImportanceScore() {
        return importanceScore;
    }

    public void setImportanceScore(Integer importanceScore) {
        this.importanceScore = importanceScore;
    }

    public String getNoveltyState() {
        return noveltyState;
    }

    public void setNoveltyState(String noveltyState) {
        this.noveltyState = noveltyState;
    }

    public Integer getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(Integer evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public Integer getArticleCount() {
        return articleCount;
    }

    public void setArticleCount(Integer articleCount) {
        this.articleCount = articleCount;
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
