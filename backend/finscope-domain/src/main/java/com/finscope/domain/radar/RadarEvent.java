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
    private int hotspotScore;
    private String hotspotExplanation;
    private int priorityScore;
    private String scoreExplanation;
    private int watchlistRelevance;
    private String watchlistExplanation;
    private String uncertainty;
    private String nextObservation;
    private String evidenceStatus;
    private String evidenceSummary;
    private String evidenceWarning;
    private String evidenceFingerprint;
    private int evidenceCount;
    private int evidenceSourceCount;
    private LocalDateTime evidenceUpdatedAt;
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
    public int getHotspotScore() { return hotspotScore; }
    public void setHotspotScore(int hotspotScore) { this.hotspotScore = hotspotScore; }
    public String getHotspotExplanation() { return hotspotExplanation; }
    public void setHotspotExplanation(String hotspotExplanation) { this.hotspotExplanation = hotspotExplanation; }
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
    public String getEvidenceStatus() { return evidenceStatus; }
    public void setEvidenceStatus(String evidenceStatus) { this.evidenceStatus = evidenceStatus; }
    public String getEvidenceSummary() { return evidenceSummary; }
    public void setEvidenceSummary(String evidenceSummary) { this.evidenceSummary = evidenceSummary; }
    public String getEvidenceWarning() { return evidenceWarning; }
    public void setEvidenceWarning(String evidenceWarning) { this.evidenceWarning = evidenceWarning; }
    public String getEvidenceFingerprint() { return evidenceFingerprint; }
    public void setEvidenceFingerprint(String evidenceFingerprint) { this.evidenceFingerprint = evidenceFingerprint; }
    public int getEvidenceCount() { return evidenceCount; }
    public void setEvidenceCount(int evidenceCount) { this.evidenceCount = evidenceCount; }
    public int getEvidenceSourceCount() { return evidenceSourceCount; }
    public void setEvidenceSourceCount(int evidenceSourceCount) { this.evidenceSourceCount = evidenceSourceCount; }
    public LocalDateTime getEvidenceUpdatedAt() { return evidenceUpdatedAt; }
    public void setEvidenceUpdatedAt(LocalDateTime evidenceUpdatedAt) { this.evidenceUpdatedAt = evidenceUpdatedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
