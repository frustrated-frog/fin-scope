package com.finscope.domain.quant.data;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class QuantDataset {
    private Long id;
    private String name;
    private String market;
    private String universeType;
    private String sourceType;
    private String dataKind;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String fingerprint;
    private String qualitySummary;
    private long revision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getUniverseType() { return universeType; }
    public void setUniverseType(String universeType) { this.universeType = universeType; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getDataKind() { return dataKind; }
    public void setDataKind(String dataKind) { this.dataKind = dataKind; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getQualitySummary() { return qualitySummary; }
    public void setQualitySummary(String qualitySummary) { this.qualitySummary = qualitySummary; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
