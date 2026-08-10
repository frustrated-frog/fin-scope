package com.finscope.domain.supplychain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 某只股票当前可用的产业链证据快照。 */
public class StockSupplyChainSnapshot {
    private Long id;
    private Long instrumentId;
    private String companyCode;
    private String companyName;
    private String summary;
    private String position;
    private String limitations;
    private String schemaVersion;
    private String model;
    private LocalDate evidenceAsOf;
    private LocalDateTime generatedAt;
    private LocalDateTime updatedAt;
    private List<StockSupplyChainNode> nodes = new ArrayList<StockSupplyChainNode>();
    private List<StockSupplyChainEvidence> evidence = new ArrayList<StockSupplyChainEvidence>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }
    public String getCompanyCode() { return companyCode; }
    public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getLimitations() { return limitations; }
    public void setLimitations(String limitations) { this.limitations = limitations; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public LocalDate getEvidenceAsOf() { return evidenceAsOf; }
    public void setEvidenceAsOf(LocalDate evidenceAsOf) { this.evidenceAsOf = evidenceAsOf; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<StockSupplyChainNode> getNodes() { return nodes; }
    public void setNodes(List<StockSupplyChainNode> nodes) {
        this.nodes = nodes == null
                ? new ArrayList<StockSupplyChainNode>() : new ArrayList<StockSupplyChainNode>(nodes);
    }
    public List<StockSupplyChainEvidence> getEvidence() { return evidence; }
    public void setEvidence(List<StockSupplyChainEvidence> evidence) {
        this.evidence = evidence == null
                ? new ArrayList<StockSupplyChainEvidence>() : new ArrayList<StockSupplyChainEvidence>(evidence);
    }
}
