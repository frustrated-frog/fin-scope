package com.finscope.domain.radar;

import java.time.LocalDateTime;

public class RadarPairDecision {
    private String pairKey;
    private String leftFingerprint;
    private String rightFingerprint;
    private boolean sameEvent;
    private double confidence;
    private String reason;
    private String decisionSource;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static String pairKey(String firstFingerprint, String secondFingerprint) {
        if (blank(firstFingerprint) || blank(secondFingerprint)) {
            throw new IllegalArgumentException("雷达信号语义指纹不能为空");
        }
        String first = firstFingerprint.trim();
        String second = secondFingerprint.trim();
        return first.compareTo(second) <= 0 ? first + ":" + second : second + ":" + first;
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    public String getPairKey() { return pairKey; }
    public void setPairKey(String pairKey) { this.pairKey = pairKey; }
    public String getLeftFingerprint() { return leftFingerprint; }
    public void setLeftFingerprint(String leftFingerprint) { this.leftFingerprint = leftFingerprint; }
    public String getRightFingerprint() { return rightFingerprint; }
    public void setRightFingerprint(String rightFingerprint) { this.rightFingerprint = rightFingerprint; }
    public boolean isSameEvent() { return sameEvent; }
    public void setSameEvent(boolean sameEvent) { this.sameEvent = sameEvent; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getDecisionSource() { return decisionSource; }
    public void setDecisionSource(String decisionSource) { this.decisionSource = decisionSource; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
