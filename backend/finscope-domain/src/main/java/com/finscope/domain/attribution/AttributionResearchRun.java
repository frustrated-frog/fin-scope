package com.finscope.domain.attribution;

import lombok.Data;

import java.time.LocalDateTime;

/** 一份归因报告对应的受控研究运行状态。 */
@Data
public class AttributionResearchRun {
    private Long id;
    private Long reportId;
    private String status;
    private String planJson;
    private String budgetJson;
    private String currentStep;
    private String terminationReason;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
