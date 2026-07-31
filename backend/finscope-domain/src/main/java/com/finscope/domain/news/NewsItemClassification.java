package com.finscope.domain.news;

import java.time.LocalDateTime;

public final class NewsItemClassification {
    private final String itemId;
    private final String status;
    private final String categoryCode;
    private final double confidence;
    private final String reason;
    private final String modelName;
    private final String errorMessage;
    private final String manualCategoryCode;
    private final String manualReason;
    private final String reviewStatus;
    private final LocalDateTime reviewedAt;
    private final LocalDateTime updatedAt;

    public NewsItemClassification(String itemId, String status, String categoryCode, double confidence,
                                  String reason, String modelName, String errorMessage, LocalDateTime updatedAt) {
        this(itemId, status, categoryCode, confidence, reason, modelName, errorMessage,
                null, null, null, null, updatedAt);
    }

    public NewsItemClassification(String itemId, String status, String categoryCode, double confidence,
                                  String reason, String modelName, String errorMessage,
                                  String manualCategoryCode, String manualReason, String reviewStatus,
                                  LocalDateTime reviewedAt, LocalDateTime updatedAt) {
        this.itemId = itemId;
        this.status = status;
        this.categoryCode = categoryCode;
        this.confidence = confidence;
        this.reason = reason;
        this.modelName = modelName;
        this.errorMessage = errorMessage;
        this.manualCategoryCode = manualCategoryCode;
        this.manualReason = manualReason;
        this.reviewStatus = reviewStatus;
        this.reviewedAt = reviewedAt;
        this.updatedAt = updatedAt;
    }

    public String getItemId() { return itemId; }
    public String getStatus() { return status; }
    public String getCategoryCode() { return categoryCode; }
    public double getConfidence() { return confidence; }
    public String getReason() { return reason; }
    public String getModelName() { return modelName; }
    public String getErrorMessage() { return errorMessage; }
    public String getManualCategoryCode() { return manualCategoryCode; }
    public String getManualReason() { return manualReason; }
    public String getReviewStatus() { return reviewStatus; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getEffectiveCategoryCode() {
        return manualCategoryCode == null ? categoryCode : manualCategoryCode;
    }
    public boolean isPendingReview() { return "PENDING_REVIEW".equals(reviewStatus); }
    public boolean isManuallyReviewed() { return manualCategoryCode != null; }
}
