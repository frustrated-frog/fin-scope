package com.finscope.domain.radar;

import java.time.LocalDateTime;

public class RadarSignal {
    private Long id;
    private String itemId;
    private String providerCode;
    private String sourceName;
    private String sourceTier;
    private String categoryCode;
    private String title;
    private String content;
    private String url;
    private LocalDateTime publishedAt;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private String contentHash;
    private String status;
    private Integer sourceRank;
    private Integer previousSourceRank;
    private double sourceWeight;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceTier() { return sourceTier; }
    public void setSourceTier(String sourceTier) { this.sourceTier = sourceTier; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(LocalDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getSourceRank() { return sourceRank; }
    public void setSourceRank(Integer sourceRank) { this.sourceRank = sourceRank; }
    public Integer getPreviousSourceRank() { return previousSourceRank; }
    public void setPreviousSourceRank(Integer previousSourceRank) { this.previousSourceRank = previousSourceRank; }
    public double getSourceWeight() { return sourceWeight; }
    public void setSourceWeight(double sourceWeight) { this.sourceWeight = sourceWeight; }
}
