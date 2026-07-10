package com.finscope.domain.intake;

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

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public Integer getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(Integer evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public Integer getLearningTaskCount() {
        return learningTaskCount;
    }

    public void setLearningTaskCount(Integer learningTaskCount) {
        this.learningTaskCount = learningTaskCount;
    }

    public Integer getContentIdeaCount() {
        return contentIdeaCount;
    }

    public void setContentIdeaCount(Integer contentIdeaCount) {
        this.contentIdeaCount = contentIdeaCount;
    }

    public String getWorkflowStatus() {
        return workflowStatus;
    }

    public void setWorkflowStatus(String workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    public String getWorkflowSummary() {
        return workflowSummary;
    }

    public void setWorkflowSummary(String workflowSummary) {
        this.workflowSummary = workflowSummary;
    }

    public String getWorkflowErrorMessage() {
        return workflowErrorMessage;
    }

    public void setWorkflowErrorMessage(String workflowErrorMessage) {
        this.workflowErrorMessage = workflowErrorMessage;
    }
}
