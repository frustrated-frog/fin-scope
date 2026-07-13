package com.finscope.domain.attribution;

import lombok.Data;

import java.time.LocalDateTime;

/** 归因运行中的一个可恢复研究步骤。 */
@Data
public class AttributionResearchStep {
    private Long id;
    private Long runId;
    private String stepId;
    private String track;
    private String status;
    private String inputSummary;
    private String outputSummary;
    private Integer attempt;
    private Integer maxAttempts;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
