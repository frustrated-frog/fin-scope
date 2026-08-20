package com.finscope.domain.investmentobservation;

import java.time.LocalDateTime;

public class InvestmentObservationRefreshResult {
    private int scannedCount;
    private int updatedCount;
    private int preservedCount;
    private int focusCount;
    private int trackingCount;
    private int learningCount;
    private LocalDateTime refreshedAt;

    public int getScannedCount() { return scannedCount; }
    public void setScannedCount(int scannedCount) { this.scannedCount = scannedCount; }
    public int getUpdatedCount() { return updatedCount; }
    public void setUpdatedCount(int updatedCount) { this.updatedCount = updatedCount; }
    public int getPreservedCount() { return preservedCount; }
    public void setPreservedCount(int preservedCount) { this.preservedCount = preservedCount; }
    public int getFocusCount() { return focusCount; }
    public void setFocusCount(int focusCount) { this.focusCount = focusCount; }
    public int getTrackingCount() { return trackingCount; }
    public void setTrackingCount(int trackingCount) { this.trackingCount = trackingCount; }
    public int getLearningCount() { return learningCount; }
    public void setLearningCount(int learningCount) { this.learningCount = learningCount; }
    public LocalDateTime getRefreshedAt() { return refreshedAt; }
    public void setRefreshedAt(LocalDateTime refreshedAt) { this.refreshedAt = refreshedAt; }
}
