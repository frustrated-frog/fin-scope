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
