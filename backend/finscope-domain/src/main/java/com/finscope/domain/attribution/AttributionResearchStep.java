package com.finscope.domain.attribution;

import lombok.Data;

import java.time.LocalDateTime;

/** 归因运行中的一个可恢复研究步骤。 */
@Data
public class AttributionResearchStep {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 运行 ID。
     */
    private Long runId;
    /**
     * 步骤 ID。
     */
    private String stepId;
    /**
     * 研究轨道。
     */
    private String track;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 输入摘要。
     */
    private String inputSummary;
    /**
     * 输出摘要。
     */
    private String outputSummary;
    /**
     * 尝试次数。
     */
    private Integer attempt;
    /**
     * 最大尝试次数。
     */
    private Integer maxAttempts;
    /**
     * 错误信息。
     */
    private String errorMessage;
    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 结束时间。
     */
    private LocalDateTime endedAt;
}
