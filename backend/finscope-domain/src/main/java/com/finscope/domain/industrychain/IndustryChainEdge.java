package com.finscope.domain.industrychain;

import java.util.ArrayList;
import java.util.List;

/** 产业链节点之间的一条有向、可追溯关系。 */
public class IndustryChainEdge {
    private String edgeKey;
    private String sourceKey;
    private String targetKey;
    private String type;
    private String nature;
    private String description;
    private String confidence;
    private String strength;
    private String directionNote;
    private List<String> evidenceRefs = new ArrayList<String>();

    public String getEdgeKey() { return edgeKey; }
    public void setEdgeKey(String edgeKey) { this.edgeKey = edgeKey; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getNature() { return nature; }
    public void setNature(String nature) { this.nature = nature; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public String getStrength() { return strength; }
    public void setStrength(String strength) { this.strength = strength; }
    public String getDirectionNote() { return directionNote; }
    public void setDirectionNote(String directionNote) { this.directionNote = directionNote; }
    public List<String> getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(List<String> evidenceRefs) {
        this.evidenceRefs = evidenceRefs == null ? new ArrayList<String>() : new ArrayList<String>(evidenceRefs);
    }
}
