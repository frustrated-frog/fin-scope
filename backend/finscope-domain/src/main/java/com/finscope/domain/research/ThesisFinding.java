package com.finscope.domain.research;

import java.time.LocalDateTime;

/**
 * 可追溯的简短发现，用于支持、挑战或暂时悬置某个研究命题。
 */
public class ThesisFinding {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 研究命题 ID。
     */
    private Long thesisId;
    /**
     * 研究运行 ID。
     */
    private Long researchRunId;
    /**
     * 立场。
     */
    private String stance;
    /**
     * 摘要。
     */
    private String summary;
    /**
     * 证据 ID。
     */
    private Long evidenceId;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getThesisId() { return thesisId; }
    public void setThesisId(Long thesisId) { this.thesisId = thesisId; }
    public Long getResearchRunId() { return researchRunId; }
    public void setResearchRunId(Long researchRunId) { this.researchRunId = researchRunId; }
    public String getStance() { return stance; }
    public void setStance(String stance) { this.stance = stance; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Long getEvidenceId() { return evidenceId; }
    public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
