package com.finscope.domain.marketdata;

import java.time.LocalDateTime;

/** 一次网关刷新审计摘要；不保存逐标的明细。 */
public final class MarketDataRefreshRun {
    /**
     * 主键 ID。
     */
    private final long id;
    /**
     * 市场数据能力类型。
     */
    private final MarketDataCapability capability;
    /**
     * 数据作用域摘要。
     */
    private final String scopeSummary;
    /**
     * 触发类型。
     */
    private final String triggerType;
    /**
     * 当前状态。
     */
    private final String status;
    /**
     * 开始时间。
     */
    private final LocalDateTime startedAt;
    /**
     * 完成时间。
     */
    private final LocalDateTime finishedAt;
    /**
     * 请求数量。
     */
    private final int requestedCount;
    /**
     * 新鲜数据数量。
     */
    private final int freshCount;
    /**
     * 过期数据数量。
     */
    private final int staleCount;
    /**
     * 失败数量。
     */
    private final int failedCount;
    /**
     * 本次选中的数据源。
     */
    private final String selectedSources;
    /**
     * 警告信息。
     */
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
