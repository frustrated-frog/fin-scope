package com.finscope.domain.strategy;

import java.time.LocalDateTime;

public class StrategyStockThesis {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 标的 ID。
     */
    private Long instrumentId;
    /**
     * 阶段。
     */
    private String stage;
    /**
     * 研究命题内容。
     */
    private String thesis;
    /**
     * 买入条件。
     */
    private String buyConditions;
    /**
     * 失效条件。
     */
    private String invalidationConditions;
    /**
     * 观察重点。
     */
    private String watchFocus;
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
    /**
     * 业务编码。
     */
    private String code;
    /**
     * 名称。
     */
    private String name;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getThesis() { return thesis; }
    public void setThesis(String thesis) { this.thesis = thesis; }
    public String getBuyConditions() { return buyConditions; }
    public void setBuyConditions(String buyConditions) { this.buyConditions = buyConditions; }
    public String getInvalidationConditions() { return invalidationConditions; }
    public void setInvalidationConditions(String invalidationConditions) { this.invalidationConditions = invalidationConditions; }
    public String getWatchFocus() { return watchFocus; }
    public void setWatchFocus(String watchFocus) { this.watchFocus = watchFocus; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
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
