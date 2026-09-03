package com.finscope.domain.news;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class NewsItemClassification {
    private String itemId;
    private String status;
    private String categoryCode;
    private double confidence;
    private String reason;
    private String modelName;
    private String errorMessage;
    private String manualCategoryCode;
    private String manualReason;
    private String reviewStatus;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public NewsItemClassification(String itemId, String status, String categoryCode, double confidence,
                                  String reason, String modelName, String errorMessage, LocalDateTime updatedAt) {
        this(itemId, status, categoryCode, confidence, reason, modelName, errorMessage,
                null, null, null, null, updatedAt, updatedAt);
    }

    public NewsItemClassification(String itemId, String status, String categoryCode, double confidence,
                                  String reason, String modelName, String errorMessage,
                                  String manualCategoryCode, String manualReason, String reviewStatus,
                                  LocalDateTime reviewedAt, LocalDateTime updatedAt) {
        this(itemId, status, categoryCode, confidence, reason, modelName, errorMessage,
                manualCategoryCode, manualReason, reviewStatus, reviewedAt, updatedAt, updatedAt);
    }

    public NewsItemClassification(String itemId, String status, String categoryCode, double confidence,
                                  String reason, String modelName, String errorMessage,
                                  String manualCategoryCode, String manualReason, String reviewStatus,
                                  LocalDateTime reviewedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
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
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getEffectiveCategoryCode() {
        return manualCategoryCode == null ? categoryCode : manualCategoryCode;
    }

    public boolean isPendingReview() {
        return "PENDING_REVIEW".equals(reviewStatus);
    }

    public boolean isManuallyReviewed() {
        return manualCategoryCode != null;
    }
}
