package com.finscope.domain.research;

import java.time.LocalDateTime;

/** A reviewable event-level judgement, distinct from long-lived ResearchThesis. */
public class EventEvidenceThesis {
    private Long id; private Long eventId; private String statement; private String kind; private String status;
    private String rationale; private String evidenceGap; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getEventId() { return eventId; } public void setEventId(Long eventId) { this.eventId = eventId; }
    public String getStatement() { return statement; } public void setStatement(String statement) { this.statement = statement; }
    public String getKind() { return kind; } public void setKind(String kind) { this.kind = kind; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public String getRationale() { return rationale; } public void setRationale(String rationale) { this.rationale = rationale; }
    public String getEvidenceGap() { return evidenceGap; } public void setEvidenceGap(String evidenceGap) { this.evidenceGap = evidenceGap; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
