package com.finscope.web.request;

public class ArchiveInvestmentObservationRequest {
    private Integer revision;
    private String reason;

    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
