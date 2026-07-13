package com.finscope.web.request.knowledge;

public class DismissKnowledgeTaskRequest {
    private String reason;
    private Long expectedRevision;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getExpectedRevision() {
        return expectedRevision;
    }

    public void setExpectedRevision(Long expectedRevision) {
        this.expectedRevision = expectedRevision;
    }
}
