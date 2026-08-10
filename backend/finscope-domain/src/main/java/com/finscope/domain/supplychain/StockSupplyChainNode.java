package com.finscope.domain.supplychain;

import java.util.ArrayList;
import java.util.List;

/** 产业链中的一个可追溯关系节点。 */
public class StockSupplyChainNode {
    private String layer;
    private String name;
    private String relationType;
    private String description;
    private String confidence;
    private List<String> evidenceRefs = new ArrayList<String>();

    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public List<String> getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(List<String> evidenceRefs) {
        this.evidenceRefs = evidenceRefs == null
                ? new ArrayList<String>() : new ArrayList<String>(evidenceRefs);
    }
}
