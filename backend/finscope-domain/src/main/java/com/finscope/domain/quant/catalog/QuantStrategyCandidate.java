package com.finscope.domain.quant.catalog;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class QuantStrategyCandidate extends QuantStrategyCatalogEntry {
    private Long id;
    private String sourceCode;
    private String sourceCommitSha;
    private String assetClass;
    private String compatibilityStatus;
    private String adaptationNote;
    private List<String> mappedFactors = new ArrayList<String>();
    private List<String> missingFactors = new ArrayList<String>();
    private boolean archived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getSourceCommitSha() { return sourceCommitSha; }
    public void setSourceCommitSha(String sourceCommitSha) { this.sourceCommitSha = sourceCommitSha; }
    public String getAssetClass() { return assetClass; }
    public void setAssetClass(String assetClass) { this.assetClass = assetClass; }
    public String getCompatibilityStatus() { return compatibilityStatus; }
    public void setCompatibilityStatus(String compatibilityStatus) { this.compatibilityStatus = compatibilityStatus; }
    public String getAdaptationNote() { return adaptationNote; }
    public void setAdaptationNote(String adaptationNote) { this.adaptationNote = adaptationNote; }
    public List<String> getMappedFactors() { return mappedFactors; }
    public void setMappedFactors(List<String> mappedFactors) { this.mappedFactors = mappedFactors; }
    public List<String> getMissingFactors() { return missingFactors; }
    public void setMissingFactors(List<String> missingFactors) { this.missingFactors = missingFactors; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
