package com.finscope.domain.marketintel;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MarketIntelRefreshRun {
    public enum Status {
        PENDING, RUNNING, SUCCEEDED, PARTIAL, FAILED;
        public boolean isTerminal() { return this == SUCCEEDED || this == PARTIAL || this == FAILED; }
    }
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 标的 ID。
     */
    private Long instrumentId;
    /**
     * 触发类型。
     */
    private String triggerType;
    /**
     * 当前状态。
     */
    private Status status;
    /**
     * 成功数量。
     */
    private int successCount;
    /**
     * 失败数量。
     */
    private int failureCount;
    /**
     * 错误类型。
     */
    private String errorType;
    /**
     * 错误信息。
     */
    private String errorMessage;
    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 完成时间。
     */
    private LocalDateTime finishedAt;
}
