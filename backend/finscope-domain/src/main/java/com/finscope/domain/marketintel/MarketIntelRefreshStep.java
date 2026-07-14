package com.finscope.domain.marketintel;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MarketIntelRefreshStep {
    public enum Status {
        PENDING, RUNNING, SUCCEEDED, EMPTY, FAILED, SKIPPED;
        public boolean isTerminal() { return this == SUCCEEDED || this == EMPTY || this == FAILED || this == SKIPPED; }
    }
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 运行 ID。
     */
    private Long runId;
    /**
     * 维度。
     */
    private String dimension;
    /**
     * 数据提供方编码。
     */
    private String providerCode;
    /**
     * 尝试次数。
     */
    private int attempt;
    /**
     * 当前状态。
     */
    private Status status;
    /**
     * 是否使用兜底结果。
     */
    private boolean fallbackUsed;
    /**
     * 错误类型。
     */
    private String errorType;
    /**
     * 错误信息。
     */
    private String errorMessage;
    /**
     * 输出数量。
     */
    private int outputCount;
    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 完成时间。
     */
    private LocalDateTime finishedAt;
}
