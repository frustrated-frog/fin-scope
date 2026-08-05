package com.finscope.domain.radar;

import java.time.LocalDateTime;

public class RadarRefreshRun {
    private Long id;
    private String runKey;
    private String triggerType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private int sourceCount;
    private int signalCount;
    private int eventCount;
    private String warning;
    private String error;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunKey() { return runKey; }
    public void setRunKey(String runKey) { this.runKey = runKey; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public int getSourceCount() { return sourceCount; }
    public void setSourceCount(int sourceCount) { this.sourceCount = sourceCount; }
    public int getSignalCount() { return signalCount; }
    public void setSignalCount(int signalCount) { this.signalCount = signalCount; }
    public int getEventCount() { return eventCount; }
    public void setEventCount(int eventCount) { this.eventCount = eventCount; }
    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
