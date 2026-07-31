package com.finscope.web.request.knowledge;

public class InvestmentRecognitionActionRequest {
    private Long expectedRevision;
    private String status;

    public Long getExpectedRevision() { return expectedRevision; }
    public void setExpectedRevision(Long expectedRevision) { this.expectedRevision = expectedRevision; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
