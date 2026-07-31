package com.finscope.service.news;

public final class NewsClassificationReviewRequest {
    private String itemId;
    private String categoryCode;
    private String reason;

    public NewsClassificationReviewRequest() {
    }

    public NewsClassificationReviewRequest(String itemId, String categoryCode, String reason) {
        this.itemId = itemId;
        this.categoryCode = categoryCode;
        this.reason = reason;
    }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
