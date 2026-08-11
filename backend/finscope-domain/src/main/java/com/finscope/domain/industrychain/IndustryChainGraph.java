package com.finscope.domain.industrychain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 一个不可变修订所承载的产业链图谱聚合。 */
public class IndustryChainGraph {
    private Long chainId;
    private Long revisionId;
    private String name;
    private String summary;
    private String limitations;
    private String schemaVersion;
    private String model;
    private LocalDateTime generatedAt;
    private List<IndustryChainNode> nodes = new ArrayList<IndustryChainNode>();
    private List<IndustryChainEdge> edges = new ArrayList<IndustryChainEdge>();
    private List<IndustryChainEvidence> evidence = new ArrayList<IndustryChainEvidence>();

    public Long getChainId() { return chainId; }
    public void setChainId(Long chainId) { this.chainId = chainId; }
    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long revisionId) { this.revisionId = revisionId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getLimitations() { return limitations; }
    public void setLimitations(String limitations) { this.limitations = limitations; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public List<IndustryChainNode> getNodes() { return nodes; }
    public void setNodes(List<IndustryChainNode> nodes) {
        this.nodes = nodes == null ? new ArrayList<IndustryChainNode>() : new ArrayList<IndustryChainNode>(nodes);
    }
    public List<IndustryChainEdge> getEdges() { return edges; }
    public void setEdges(List<IndustryChainEdge> edges) {
        this.edges = edges == null ? new ArrayList<IndustryChainEdge>() : new ArrayList<IndustryChainEdge>(edges);
    }
    public List<IndustryChainEvidence> getEvidence() { return evidence; }
    public void setEvidence(List<IndustryChainEvidence> evidence) {
        this.evidence = evidence == null
                ? new ArrayList<IndustryChainEvidence>() : new ArrayList<IndustryChainEvidence>(evidence);
    }
}
