package com.finscope.service.research.evaluation;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ResearchBenchmarkCaseResult {
    private final String id;
    private final String question;
    private final ResearchGroundingMetrics metrics;

    ResearchBenchmarkCaseResult(String id, String question, ResearchGroundingMetrics metrics) {
        this.id = id;
        this.question = question;
        this.metrics = metrics;
    }

    public String getId() { return id; }
    public String getQuestion() { return question; }
    public ResearchGroundingMetrics getMetrics() { return metrics; }

    Map<String, Object> canonical() {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("id", id);
        value.put("question", question);
        Map<String, Object> metric = new LinkedHashMap<String, Object>();
        metric.put("claimCount", metrics.getClaimCount());
        metric.put("citedClaimCount", metrics.getCitedClaimCount());
        metric.put("supportedClaimCount", metrics.getSupportedClaimCount());
        metric.put("citationCoverageRate", metrics.getCitationCoverageRate());
        metric.put("claimSupportRate", metrics.getClaimSupportRate());
        metric.put("keyFactCoverageRate", metrics.getKeyFactCoverageRate());
        metric.put("primarySourceRatio", metrics.getPrimarySourceRatio());
        metric.put("counterEvidenceCoverage", metrics.getCounterEvidenceCoverage());
        metric.put("citationAccessibilityRate", metrics.getCitationAccessibilityRate());
        metric.put("freshnessRate", metrics.getFreshnessRate());
        value.put("metrics", metric);
        return value;
    }
}
