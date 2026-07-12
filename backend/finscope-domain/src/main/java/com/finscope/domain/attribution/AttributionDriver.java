package com.finscope.domain.attribution;

import java.util.List;

/**
 * 归因驱动因素：报告中的一条"原因"，带影响力与置信度双维度。
 * 作为 AttributionReport 的子对象，持久化时序列化为 JSON。
 */
public class AttributionDriver {
    /** 原因描述 */
    private String claim;
    /** 影响力：HIGH | MID | LOW */
    private String impactLevel;
    /** 置信度：HIGH | MID | LOW */
    private String confidence;
    /** 支撑说明 */
    private String detail;
    /** 关联证据的 url 列表（指向 AttributionEvidence） */
    private List<String> evidenceUrls;
    /** 支撑该驱动的可核验事实。 */
    private List<String> facts;
    /** 事件如何传导到预期、资金或估值，再影响价格。 */
    private String transmissionPath;
    /** 与该驱动相冲突或限制其解释力的信息。 */
    private String counterEvidence;
    /** 后续验证该驱动的观察窗口。 */
    private String observationWindow;

    public String getClaim() {
        return claim;
    }

    public void setClaim(String claim) {
        this.claim = claim;
    }

    public String getImpactLevel() {
        return impactLevel;
    }

    public void setImpactLevel(String impactLevel) {
        this.impactLevel = impactLevel;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public List<String> getEvidenceUrls() {
        return evidenceUrls;
    }

    public void setEvidenceUrls(List<String> evidenceUrls) {
        this.evidenceUrls = evidenceUrls;
    }

    public List<String> getFacts() { return facts; }
    public void setFacts(List<String> facts) { this.facts = facts; }
    public String getTransmissionPath() { return transmissionPath; }
    public void setTransmissionPath(String transmissionPath) { this.transmissionPath = transmissionPath; }
    public String getCounterEvidence() { return counterEvidence; }
    public void setCounterEvidence(String counterEvidence) { this.counterEvidence = counterEvidence; }
    public String getObservationWindow() { return observationWindow; }
    public void setObservationWindow(String observationWindow) { this.observationWindow = observationWindow; }
}
