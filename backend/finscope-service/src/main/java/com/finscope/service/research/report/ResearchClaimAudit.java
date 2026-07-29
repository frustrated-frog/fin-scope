package com.finscope.service.research.report;

import java.util.Collections;
import java.util.List;

public final class ResearchClaimAudit {
    private final List<ResearchClaimAuditItem> items;

    ResearchClaimAudit(List<ResearchClaimAuditItem> items) {
        this.items = Collections.unmodifiableList(items);
    }

    public List<ResearchClaimAuditItem> getItems() { return items; }
    public int getClaimCount() { return items.size(); }
    public int getSupportedCount() { return count("SUPPORTED"); }
    public int getPartialCount() { return count("PARTIAL"); }
    public int getUnsupportedCount() { return count("UNSUPPORTED"); }
    public int getConflictCount() { return count("CONFLICT"); }
    public boolean hasBlockingIssues() { return getPartialCount() + getUnsupportedCount() + getConflictCount() > 0; }

    private int count(String status) {
        int count = 0;
        for (ResearchClaimAuditItem item : items) if (status.equals(item.getStatus())) count++;
        return count;
    }
}
