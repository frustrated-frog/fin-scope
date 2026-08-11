package com.finscope.domain.industrychain;

import java.time.LocalDateTime;

/** 一个用户维护的产业链主题。 */
public class IndustryChain {
    private Long id;
    private String name;
    private String normalizedName;
    private String summary;
    private Long currentRevisionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Long getCurrentRevisionId() { return currentRevisionId; }
    public void setCurrentRevisionId(Long currentRevisionId) { this.currentRevisionId = currentRevisionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
