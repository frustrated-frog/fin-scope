package com.finscope.domain.marketdata;

import java.time.LocalDateTime;

/** 一次网关刷新审计摘要；不保存逐标的明细。 */
public final class MarketDataRefreshRun {
    private final long id;
    private final MarketDataCapability capability;
    private final String scopeSummary;
    private final String triggerType;
    private final String status;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final int requestedCount;
    private final int freshCount;
    private final int staleCount;
    private final int failedCount;
    private final String selectedSources;
    private final String warningMessage;

    public MarketDataRefreshRun(long id, MarketDataCapability capability, String scopeSummary,
                                String triggerType, String status, LocalDateTime startedAt,
                                LocalDateTime finishedAt, int requestedCount, int freshCount,
                                int staleCount, int failedCount, String selectedSources,
                                String warningMessage) {
        this.id = id;
        this.capability = capability;
        this.scopeSummary = scopeSummary;
        this.triggerType = triggerType;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.requestedCount = requestedCount;
        this.freshCount = freshCount;
        this.staleCount = staleCount;
        this.failedCount = failedCount;
        this.selectedSources = selectedSources;
        this.warningMessage = warningMessage;
    }

    public long getId() { return id; }
    public MarketDataCapability getCapability() { return capability; }
    public String getScopeSummary() { return scopeSummary; }
    public String getTriggerType() { return triggerType; }
    public String getStatus() { return status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public int getRequestedCount() { return requestedCount; }
    public int getFreshCount() { return freshCount; }
    public int getStaleCount() { return staleCount; }
    public int getFailedCount() { return failedCount; }
    public String getSelectedSources() { return selectedSources; }
    public String getWarningMessage() { return warningMessage; }
}
