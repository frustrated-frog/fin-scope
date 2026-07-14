package com.finscope.domain.intake;

import lombok.Data;

@Data
public class PromoteIntakeCandidateResponse {
    private Long candidateId;
    private Long articleId;
    private String status;
    private Long eventId;
    private String eventTitle;
    private Integer evidenceCount;
    private Integer learningTaskCount;
    private Integer contentIdeaCount;
    private String workflowStatus;
    private String workflowSummary;
    private String workflowErrorMessage;
}
