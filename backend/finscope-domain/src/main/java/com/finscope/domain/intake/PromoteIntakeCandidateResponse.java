package com.finscope.domain.intake;

import lombok.Data;

@Data
public class PromoteIntakeCandidateResponse {
    /**
     * 摄入候选 ID。
     */
    private Long candidateId;
    /**
     * 文章 ID。
     */
    private Long articleId;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 事件 ID。
     */
    private Long eventId;
    /**
     * 事件标题。
     */
    private String eventTitle;
    /**
     * 证据数量。
     */
    private Integer evidenceCount;
    /**
     * 学习任务数量。
     */
    private Integer learningTaskCount;
    /**
     * 内容选题数量。
     */
    private Integer contentIdeaCount;
    /**
     * 工作流状态。
     */
    private String workflowStatus;
    /**
     * 工作流摘要。
     */
    private String workflowSummary;
    /**
     * 工作流错误信息。
     */
    private String workflowErrorMessage;
}
