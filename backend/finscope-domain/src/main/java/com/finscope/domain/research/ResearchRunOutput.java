package com.finscope.domain.research;

import java.time.LocalDateTime;

public class ResearchRunOutput {
    private Long id;
    private Long researchRunId;
    private String outputType;
    private Long outputId;
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getResearchRunId() { return researchRunId; }
    public void setResearchRunId(Long researchRunId) { this.researchRunId = researchRunId; }
    public String getOutputType() { return outputType; }
    public void setOutputType(String outputType) { this.outputType = outputType; }
    public Long getOutputId() { return outputId; }
    public void setOutputId(Long outputId) { this.outputId = outputId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
