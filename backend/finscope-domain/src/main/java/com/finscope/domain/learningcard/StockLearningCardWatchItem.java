package com.finscope.domain.learningcard;

import java.time.LocalDateTime;

public class StockLearningCardWatchItem {
    private Long id;
    private Long runId;
    private String metric;
    private String baseline;
    private String frequency;
    private String upgradeCondition;
    private String downgradeCondition;
    private LocalDateTime nextReviewAt;
    private int sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public String getBaseline() { return baseline; }
    public void setBaseline(String baseline) { this.baseline = baseline; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public String getUpgradeCondition() { return upgradeCondition; }
    public void setUpgradeCondition(String upgradeCondition) { this.upgradeCondition = upgradeCondition; }
    public String getDowngradeCondition() { return downgradeCondition; }
    public void setDowngradeCondition(String downgradeCondition) { this.downgradeCondition = downgradeCondition; }
    public LocalDateTime getNextReviewAt() { return nextReviewAt; }
    public void setNextReviewAt(LocalDateTime nextReviewAt) { this.nextReviewAt = nextReviewAt; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
