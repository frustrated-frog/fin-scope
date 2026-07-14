package com.finscope.domain.research;

import java.time.LocalDateTime;

public class ResearchRunOutput {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 研究运行 ID。
     */
    private Long researchRunId;
    /**
     * 输出类型。
     */
    private String outputType;
    /**
     * 输出 ID。
     */
    private Long outputId;
    /**
     * 创建时间。
     */
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
