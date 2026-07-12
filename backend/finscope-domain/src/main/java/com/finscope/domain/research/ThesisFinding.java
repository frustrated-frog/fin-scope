package com.finscope.domain.research;

import java.time.LocalDateTime;

/**
 * A concise, traceable finding that supports, challenges, or leaves a thesis unresolved.
 */
public class ThesisFinding {
    private Long id;
    private Long thesisId;
    private String stance;
    private String summary;
    private Long evidenceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getThesisId() { return thesisId; }
    public void setThesisId(Long thesisId) { this.thesisId = thesisId; }
    public String getStance() { return stance; }
    public void setStance(String stance) { this.stance = stance; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Long getEvidenceId() { return evidenceId; }
    public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
