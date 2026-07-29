package com.finscope.service.research.report;

public final class ResearchClaimAuditItem {
    private final ResearchClaim claim;
    private final String status;
    private final String reason;

    ResearchClaimAuditItem(ResearchClaim claim, String status, String reason) {
        this.claim = claim;
        this.status = status;
        this.reason = reason;
    }

    public ResearchClaim getClaim() { return claim; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public boolean isBlocking() { return !"SUPPORTED".equals(status); }
}
