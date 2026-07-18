package com.finscope.domain.strategy;

import java.time.LocalDateTime;

public class StrategyPlaybook {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 业务编码。
     */
    private String code;
    private String title;
    private String scope;
    private String summary;
    private String cadence;
    private String riskBoundary;
    private String author;
    private String sourceTitle;
    private String sourceType;
    private String sourceRef;
    private String sourcePublishedAt;
    private String validationStatus;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 备注信息。
     */
    private String note;
    /**
     * 数据版本号，用于并发更新校验。
     */
    private long revision;
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
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getCadence() { return cadence; }
    public void setCadence(String cadence) { this.cadence = cadence; }
    public String getRiskBoundary() { return riskBoundary; }
    public void setRiskBoundary(String riskBoundary) { this.riskBoundary = riskBoundary; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getSourceTitle() { return sourceTitle; }
    public void setSourceTitle(String sourceTitle) { this.sourceTitle = sourceTitle; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public String getSourcePublishedAt() { return sourcePublishedAt; }
    public void setSourcePublishedAt(String sourcePublishedAt) { this.sourcePublishedAt = sourcePublishedAt; }
    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
