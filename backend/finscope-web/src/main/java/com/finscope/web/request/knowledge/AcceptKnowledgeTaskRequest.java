package com.finscope.web.request.knowledge;

public class AcceptKnowledgeTaskRequest {
    private Long topicId;
    private Long expectedRevision;

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public Long getExpectedRevision() {
        return expectedRevision;
    }

    public void setExpectedRevision(Long expectedRevision) {
        this.expectedRevision = expectedRevision;
    }
}
