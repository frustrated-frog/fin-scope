package com.finscope.domain.research;

import java.time.LocalDateTime;

/** A reviewable event-level judgement, distinct from long-lived ResearchThesis. */
public class EventEvidenceThesis {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 事件 ID。
     */
    private Long eventId;
    /**
     * 命题陈述。
     */
    private String statement;
    /**
     * 种类。
     */
    private String kind;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 理由说明。
     */
    private String rationale;
    /**
     * 证据缺口。
     */
    private String evidenceGap;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getEventId() { return eventId; } public void setEventId(Long eventId) { this.eventId = eventId; }
    public String getStatement() { return statement; } public void setStatement(String statement) { this.statement = statement; }
    public String getKind() { return kind; } public void setKind(String kind) { this.kind = kind; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public String getRationale() { return rationale; } public void setRationale(String rationale) { this.rationale = rationale; }
    public String getEvidenceGap() { return evidenceGap; } public void setEvidenceGap(String evidenceGap) { this.evidenceGap = evidenceGap; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
