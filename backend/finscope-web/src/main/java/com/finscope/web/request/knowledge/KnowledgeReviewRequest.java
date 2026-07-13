package com.finscope.web.request.knowledge;

import java.util.List;

public class KnowledgeReviewRequest {
    private String conclusion;
    private String confidence;
    private List<Long> evidenceIds;
    private Integer intervalDays;
    private Long expectedRevision;

    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public List<Long> getEvidenceIds() { return evidenceIds; }
    public void setEvidenceIds(List<Long> evidenceIds) { this.evidenceIds = evidenceIds; }
    public Integer getIntervalDays() { return intervalDays; }
    public void setIntervalDays(Integer intervalDays) { this.intervalDays = intervalDays; }
    public Long getExpectedRevision() { return expectedRevision; }
    public void setExpectedRevision(Long expectedRevision) { this.expectedRevision = expectedRevision; }
}
