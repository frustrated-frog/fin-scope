package com.finscope.domain.strategy;

import java.time.LocalDateTime;

public class StrategyHolding {
    private Long id;
    private Long instrumentId;
    private String role;
    private double targetWeight;
    private double currentWeight;
    private String note;
    private int sortOrder;
    private long revision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String code;
    private String type;
    private String name;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public double getTargetWeight() { return targetWeight; }
    public void setTargetWeight(double targetWeight) { this.targetWeight = targetWeight; }
    public double getCurrentWeight() { return currentWeight; }
    public void setCurrentWeight(double currentWeight) { this.currentWeight = currentWeight; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
