package com.finscope.domain.radar;

import java.time.LocalDateTime;

public class RadarEvent {
    private Long id;
    private String eventKey;
    private String canonicalTitle;
    private String summary;
    private String categoryCode;
    private String status;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private int sourceCount;
    private int signalCount;
    private int priorityScore;
    private String scoreExplanation;
    private int watchlistRelevance;
    private String watchlistExplanation;
    private String uncertainty;
    private String nextObservation;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public String getCanonicalTitle() { return canonicalTitle; }
    public void setCanonicalTitle(String canonicalTitle) { this.canonicalTitle = canonicalTitle; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(LocalDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public int getSourceCount() { return sourceCount; }
    public void setSourceCount(int sourceCount) { this.sourceCount = sourceCount; }
    public int getSignalCount() { return signalCount; }
    public void setSignalCount(int signalCount) { this.signalCount = signalCount; }
    public int getPriorityScore() { return priorityScore; }
    public void setPriorityScore(int priorityScore) { this.priorityScore = priorityScore; }
    public String getScoreExplanation() { return scoreExplanation; }
    public void setScoreExplanation(String scoreExplanation) { this.scoreExplanation = scoreExplanation; }
    public int getWatchlistRelevance() { return watchlistRelevance; }
    public void setWatchlistRelevance(int watchlistRelevance) { this.watchlistRelevance = watchlistRelevance; }
    public String getWatchlistExplanation() { return watchlistExplanation; }
    public void setWatchlistExplanation(String watchlistExplanation) { this.watchlistExplanation = watchlistExplanation; }
    public String getUncertainty() { return uncertainty; }
    public void setUncertainty(String uncertainty) { this.uncertainty = uncertainty; }
    public String getNextObservation() { return nextObservation; }
    public void setNextObservation(String nextObservation) { this.nextObservation = nextObservation; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
