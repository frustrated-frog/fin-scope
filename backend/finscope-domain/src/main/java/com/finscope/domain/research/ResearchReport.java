package com.finscope.domain.research;

import java.time.LocalDateTime;

/**
 * 由单次研究运行产出的、有边界的命题驱动型研究报告。
 */
public class ResearchReport {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 研究运行 ID。
     */
    private Long researchRunId;
    /**
     * 研究命题 ID。
     */
    private Long thesisId;
    /**
     * 报告类型。
     */
    private String reportType;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 标题。
     */
    private String title;
    /**
     * 研究结论。
     */
    private String conclusion;
    /**
     * 结论方向。
     */
    private String conclusionDirection;
    /**
     * 置信度。
     */
    private String confidence;
    /**
     * 执行摘要。
     */
    private String executiveSummary;
    /**
     * 内容 Markdown。
     */
    private String contentMarkdown;
    /**
     * Markdown 文件路径。
     */
    private String markdownPath;
    /**
     * 生成方式。
     */
    private String generationMode;
    /**
     * 警告信息。
     */
    private String warningMessage;
    /**
     * 证据数量。
     */
    private int evidenceCount;
    /**
     * 来源数量。
     */
    private int sourceCount;
    /**
     * 字符数。
     */
    private int characterCount;
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
    public Long getResearchRunId() { return researchRunId; }
    public void setResearchRunId(Long researchRunId) { this.researchRunId = researchRunId; }
    public Long getThesisId() { return thesisId; }
    public void setThesisId(Long thesisId) { this.thesisId = thesisId; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public String getConclusionDirection() { return conclusionDirection; }
    public void setConclusionDirection(String conclusionDirection) { this.conclusionDirection = conclusionDirection; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public String getExecutiveSummary() { return executiveSummary; }
    public void setExecutiveSummary(String executiveSummary) { this.executiveSummary = executiveSummary; }
    public String getContentMarkdown() { return contentMarkdown; }
    public void setContentMarkdown(String contentMarkdown) { this.contentMarkdown = contentMarkdown; }
    public String getMarkdownPath() { return markdownPath; }
    public void setMarkdownPath(String markdownPath) { this.markdownPath = markdownPath; }
    public String getGenerationMode() { return generationMode; }
    public void setGenerationMode(String generationMode) { this.generationMode = generationMode; }
    public String getWarningMessage() { return warningMessage; }
    public void setWarningMessage(String warningMessage) { this.warningMessage = warningMessage; }
    public int getEvidenceCount() { return evidenceCount; }
    public void setEvidenceCount(int evidenceCount) { this.evidenceCount = evidenceCount; }
    public int getSourceCount() { return sourceCount; }
    public void setSourceCount(int sourceCount) { this.sourceCount = sourceCount; }
    public int getCharacterCount() { return characterCount; }
    public void setCharacterCount(int characterCount) { this.characterCount = characterCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
