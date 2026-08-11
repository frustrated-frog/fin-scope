package com.finscope.domain.industrychain;

import java.util.ArrayList;
import java.util.List;

/** 产业链中的环节、产品或公司节点。 */
public class IndustryChainNode {
    private String nodeKey;
    private String type;
    private String name;
    private String description;
    private Integer stageOrder;
    private String stockCode;
    private String confidence;
    private List<String> evidenceRefs = new ArrayList<String>();

    public String getNodeKey() { return nodeKey; }
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getStageOrder() { return stageOrder; }
    public void setStageOrder(Integer stageOrder) { this.stageOrder = stageOrder; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public List<String> getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(List<String> evidenceRefs) {
        this.evidenceRefs = evidenceRefs == null ? new ArrayList<String>() : new ArrayList<String>(evidenceRefs);
    }
}
