package com.finscope.domain.radar;

import java.time.LocalDateTime;

public class RadarRefreshStep {
    private Long id;
    private Long runId;
    private String stepCode;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private int inputCount;
    private int outputCount;
    private String details;
    private String error;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getStepCode() { return stepCode; }
    public void setStepCode(String stepCode) { this.stepCode = stepCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public int getInputCount() { return inputCount; }
    public void setInputCount(int inputCount) { this.inputCount = inputCount; }
    public int getOutputCount() { return outputCount; }
    public void setOutputCount(int outputCount) { this.outputCount = outputCount; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
