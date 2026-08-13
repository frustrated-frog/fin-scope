package com.finscope.domain.radar;

import java.time.LocalDateTime;

/** A local observation of one radar event used to calculate change over time. */
public class RadarEventSnapshot {
    private Long id;
    private Long eventId;
    private LocalDateTime snapshotAt;
    private int signalCount;
    private int independentSourceCount;
    private double velocityScore;
    private int hotnessScore;
    private double confirmationScore;
    private double freshnessScore;
    private double rankTrendScore;
    private int confidenceScore;
    private String scoreVersion = "HOTSPOT_V1";
    private String lifecycleState;
    private String explanation;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public LocalDateTime getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(LocalDateTime snapshotAt) { this.snapshotAt = snapshotAt; }
    public int getSignalCount() { return signalCount; }
    public void setSignalCount(int signalCount) { this.signalCount = signalCount; }
    public int getIndependentSourceCount() { return independentSourceCount; }
    public void setIndependentSourceCount(int independentSourceCount) { this.independentSourceCount = independentSourceCount; }
    public double getVelocityScore() { return velocityScore; }
    public void setVelocityScore(double velocityScore) { this.velocityScore = velocityScore; }
    public int getHotnessScore() { return hotnessScore; }
    public void setHotnessScore(int hotnessScore) { this.hotnessScore = hotnessScore; }
    public double getConfirmationScore() { return confirmationScore; }
    public void setConfirmationScore(double confirmationScore) { this.confirmationScore = confirmationScore; }
    public double getFreshnessScore() { return freshnessScore; }
    public void setFreshnessScore(double freshnessScore) { this.freshnessScore = freshnessScore; }
    public double getRankTrendScore() { return rankTrendScore; }
    public void setRankTrendScore(double rankTrendScore) { this.rankTrendScore = rankTrendScore; }
    public int getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(int confidenceScore) { this.confidenceScore = confidenceScore; }
    public String getScoreVersion() { return scoreVersion; }
    public void setScoreVersion(String scoreVersion) { this.scoreVersion = scoreVersion; }
    public String getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(String lifecycleState) { this.lifecycleState = lifecycleState; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
