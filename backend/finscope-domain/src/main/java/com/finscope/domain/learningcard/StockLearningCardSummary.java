package com.finscope.domain.learningcard;

import java.time.LocalDateTime;

public class StockLearningCardSummary {
    private String code;
    private String name;
    private String status;
    private String stage;
    private String summary;
    private int completedDimensions;
    private int totalDimensions;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public int getCompletedDimensions() { return completedDimensions; }
    public void setCompletedDimensions(int completedDimensions) { this.completedDimensions = completedDimensions; }
    public int getTotalDimensions() { return totalDimensions; }
    public void setTotalDimensions(int totalDimensions) { this.totalDimensions = totalDimensions; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
