package com.finscope.domain.research.evaluation;

public class ResearchEvaluationMetric {
    private Long evaluationId;
    private String metricCode;
    private String label;
    private int score;
    private int maxScore;
    private String status;
    private String evidence;
    private String recommendation;

    public Long getEvaluationId() { return evaluationId; }
    public void setEvaluationId(Long evaluationId) { this.evaluationId = evaluationId; }
    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public int getMaxScore() { return maxScore; }
    public void setMaxScore(int maxScore) { this.maxScore = maxScore; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
}
