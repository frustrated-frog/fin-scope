package com.finscope.domain.learningcard;

import java.time.LocalDateTime;

public class StockLearningCard {
    private Long id;
    private Long instrumentId;
    private String frameworkCode;
    private Long latestRunId;
    private String status;
    private long revision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String code;
    private String name;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }
    public String getFrameworkCode() { return frameworkCode; }
    public void setFrameworkCode(String frameworkCode) { this.frameworkCode = frameworkCode; }
    public Long getLatestRunId() { return latestRunId; }
    public void setLatestRunId(Long latestRunId) { this.latestRunId = latestRunId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
