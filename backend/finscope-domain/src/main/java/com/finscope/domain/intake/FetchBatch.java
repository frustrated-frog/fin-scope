package com.finscope.domain.intake;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FetchBatch {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 信息源 ID。
     */
    private Long sourceId;
    /**
     * 信息源名称。
     */
    private String sourceName;
    /**
     * 触发类型。
     */
    private String triggerType;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 结束时间。
     */
    private LocalDateTime endedAt;
    /**
     * 回看天数。
     */
    private int lookbackDays = 3;
    /**
     * 请求条目上限。
     */
    private int maxItemsRequested = 10;
    /**
     * 原始条目数量。
     */
    private int rawItemCount;
    /**
     * 候选数量。
     */
    private int candidateCount;
    /**
     * 智能体已审核数量。
     */
    private int agentReviewedCount;
    /**
     * 重复数量。
     */
    private int duplicateCount;
    /**
     * 低价值数量。
     */
    private int lowValueCount;
    /**
     * 错误信息。
     */
    private String errorMessage;
    /**
     * 批次摘要 JSON。
     */
    private String batchSummaryJson;
    /**
     * 批次摘要文本。
     */
    private String batchSummaryText;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;

}
