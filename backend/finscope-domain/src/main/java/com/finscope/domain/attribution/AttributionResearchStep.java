package com.finscope.domain.attribution;

import java.time.LocalDateTime;

/** 归因运行中的一个可恢复研究步骤。 */
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }
    public String getTrack() { return track; }
    public void setTrack(String track) { this.track = track; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInputSummary() { return inputSummary; }
    public void setInputSummary(String inputSummary) { this.inputSummary = inputSummary; }
    public String getOutputSummary() { return outputSummary; }
    public void setOutputSummary(String outputSummary) { this.outputSummary = outputSummary; }
    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer attempt) { this.attempt = attempt; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
}
