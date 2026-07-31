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
    private final LocalDateTime updatedAt;

    public NewsItemClassification(String itemId, String status, String categoryCode, double confidence,
                                  String reason, String modelName, String errorMessage, LocalDateTime updatedAt) {
        this.itemId = itemId;
        this.status = status;
        this.categoryCode = categoryCode;
        this.confidence = confidence;
        this.reason = reason;
        this.modelName = modelName;
        this.errorMessage = errorMessage;
        this.updatedAt = updatedAt;
    }

    public String getItemId() { return itemId; }
    public String getStatus() { return status; }
    public String getCategoryCode() { return categoryCode; }
    public double getConfidence() { return confidence; }
    public String getReason() { return reason; }
    public String getModelName() { return modelName; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
