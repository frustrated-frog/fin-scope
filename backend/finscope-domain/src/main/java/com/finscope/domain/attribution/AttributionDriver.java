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
}