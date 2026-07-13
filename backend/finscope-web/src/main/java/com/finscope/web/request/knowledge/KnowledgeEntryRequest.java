package com.finscope.web.request.knowledge;

import java.util.List;

public class KnowledgeEntryRequest {
    private Long topicId;
    private String markdown;
    private String confidence;
    private List<Long> evidenceIds;
    private Long expectedTaskRevision;
    private Long expectedEntryRevision;

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public List<Long> getEvidenceIds() {
        return evidenceIds;
    }

    public void setEvidenceIds(List<Long> evidenceIds) {
        this.evidenceIds = evidenceIds;
    }

    public Long getExpectedTaskRevision() {
        return expectedTaskRevision;
    }

    public void setExpectedTaskRevision(Long expectedTaskRevision) {
        this.expectedTaskRevision = expectedTaskRevision;
    }

    public Long getExpectedEntryRevision() {
        return expectedEntryRevision;
    }

    public void setExpectedEntryRevision(Long expectedEntryRevision) {
        this.expectedEntryRevision = expectedEntryRevision;
    }
}
