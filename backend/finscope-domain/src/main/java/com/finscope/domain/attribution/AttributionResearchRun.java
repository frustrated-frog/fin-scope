package com.finscope.domain.attribution;

import lombok.Data;

import java.time.LocalDateTime;

/** 一份归因报告对应的受控研究运行状态。 */
@Data
public class AttributionResearchRun {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 归因报告 ID。
     */
    private Long reportId;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 计划 JSON 内容。
     */
    private String planJson;
    /**
     * 预算 JSON 内容。
     */
    private String budgetJson;
    /**
     * 当前执行步骤。
     */
    private String currentStep;
    /**
     * 终止原因。
     */
    private String terminationReason;
    /**
     * 错误信息。
     */
    private String errorMessage;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
}
