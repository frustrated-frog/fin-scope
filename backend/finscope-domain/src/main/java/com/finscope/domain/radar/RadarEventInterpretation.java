package com.finscope.domain.radar;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RadarEventInterpretation {
    private Long id;
    private Long eventId;
    private String eventFingerprint;
    private String status;
    private Result result;
    private String failureCode;
    private String failureMessage;
    private Long durationMs;
    private boolean stale;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public String getEventFingerprint() { return eventFingerprint; }
    public void setEventFingerprint(String eventFingerprint) { this.eventFingerprint = eventFingerprint; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Result getResult() { return result; }
    public void setResult(Result result) { this.result = result; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public boolean isStale() { return stale; }
    public void setStale(boolean stale) { this.stale = stale; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public static class Result {
        private String factSummary;
        private String newDevelopment;
        private String whyItMatters;
        private List<String> impactChain = new ArrayList<String>();
        private List<String> uncertainties = new ArrayList<String>();
        private List<String> nextObservations = new ArrayList<String>();
        private List<String> evidenceRefs = new ArrayList<String>();

        public String getFactSummary() { return factSummary; }
        public void setFactSummary(String factSummary) { this.factSummary = factSummary; }
        public String getNewDevelopment() { return newDevelopment; }
        public void setNewDevelopment(String newDevelopment) { this.newDevelopment = newDevelopment; }
        public String getWhyItMatters() { return whyItMatters; }
        public void setWhyItMatters(String whyItMatters) { this.whyItMatters = whyItMatters; }
        public List<String> getImpactChain() { return impactChain; }
        public void setImpactChain(List<String> impactChain) { this.impactChain = safe(impactChain); }
        public List<String> getUncertainties() { return uncertainties; }
        public void setUncertainties(List<String> uncertainties) { this.uncertainties = safe(uncertainties); }
        public List<String> getNextObservations() { return nextObservations; }
        public void setNextObservations(List<String> nextObservations) { this.nextObservations = safe(nextObservations); }
        public List<String> getEvidenceRefs() { return evidenceRefs; }
        public void setEvidenceRefs(List<String> evidenceRefs) { this.evidenceRefs = safe(evidenceRefs); }

        private List<String> safe(List<String> values) {
            return values == null ? new ArrayList<String>() : new ArrayList<String>(values);
        }
    }
}
