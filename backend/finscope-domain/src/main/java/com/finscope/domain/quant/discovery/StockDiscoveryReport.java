package com.finscope.domain.quant.discovery;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class StockDiscoveryReport {
    private String schemaVersion;
    private String policyVersion;
    private String asOfDate;
    private String sourceCode;
    private String sourceFamily;
    private String qualityStatus;
    private String retrievedAt;
    private String dataFingerprint;
    private double budget;
    private List<String> constituentSourceFamilies = new ArrayList<String>();
    private String constituentQualityStatus;
    private List<Map<String, Object>> sectors = new ArrayList<Map<String, Object>>();
    private List<Map<String, Object>> candidates = new ArrayList<Map<String, Object>>();
    private List<Map<String, Object>> deepEvidence = new ArrayList<Map<String, Object>>();
    private List<Map<String, Object>> finalCandidates = new ArrayList<Map<String, Object>>();
    private Funnel funnel;
    private List<String> warnings = new ArrayList<String>();
    private int durationMs;
    private String rawJson;

    public int getFinalCount() {
        return funnel == null ? 0 : funnel.getFinalCount();
    }

    @Data
    public static class Funnel {
        private int rawConstituentCount;
        private int scopeExcludedCount;
        private int starMarketExcludedCount;
        private int beijingMarketExcludedCount;
        private int unsupportedScopeExcludedCount;
        private int constituentCount;
        private int admittedCount;
        private int quantifiedCount;
        private int deepReviewCount;
        private int finalCount;
    }
}
