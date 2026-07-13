package com.finscope.web.request.knowledge;

public class KnowledgeRevisionRequest {
    private Long expectedRevision;

    public Long getExpectedRevision() {
        return expectedRevision;
    }

    public void setExpectedRevision(Long expectedRevision) {
        this.expectedRevision = expectedRevision;
    }
}
