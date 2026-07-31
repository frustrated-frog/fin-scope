package com.finscope.service.news;

import java.time.LocalDateTime;

public final class NewsClassificationView {
    private final String itemId;
    private final String agentCategoryCode;
    private final String effectiveCategoryCode;
    private final double agentConfidence;
    private final String agentReason;
    private final String reviewStatus;
    private final String manualReason;
    private final LocalDateTime reviewedAt;

    public NewsClassificationView(String itemId, String agentCategoryCode, String effectiveCategoryCode,
                                  double agentConfidence, String agentReason, String reviewStatus,
                                  String manualReason, LocalDateTime reviewedAt) {
        this.itemId = itemId;
        this.agentCategoryCode = agentCategoryCode;
        this.effectiveCategoryCode = effectiveCategoryCode;
        this.agentConfidence = agentConfidence;
        this.agentReason = agentReason;
        this.reviewStatus = reviewStatus;
        this.manualReason = manualReason;
        this.reviewedAt = reviewedAt;
    }

    public String getItemId() { return itemId; }
    public String getAgentCategoryCode() { return agentCategoryCode; }
    public String getEffectiveCategoryCode() { return effectiveCategoryCode; }
    public double getAgentConfidence() { return agentConfidence; }
    public String getAgentReason() { return agentReason; }
    public String getReviewStatus() { return reviewStatus; }
    public String getManualReason() { return manualReason; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
}
