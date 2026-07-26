package com.finscope.domain.research.evaluation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchEvaluation {
    private Long id;
    private Long researchRunId;
    private String evaluatorVersion;
    private String inputFingerprint;
    private int score;
    private String gateStatus;
    private String summary;
    private List<String> criticalIssues = Collections.emptyList();
    private List<ResearchEvaluationMetric> metrics = Collections.emptyList();
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getResearchRunId() { return researchRunId; }
    public void setResearchRunId(Long researchRunId) { this.researchRunId = researchRunId; }
    public String getEvaluatorVersion() { return evaluatorVersion; }
    public void setEvaluatorVersion(String evaluatorVersion) { this.evaluatorVersion = evaluatorVersion; }
    public String getInputFingerprint() { return inputFingerprint; }
    public void setInputFingerprint(String inputFingerprint) { this.inputFingerprint = inputFingerprint; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public String getGateStatus() { return gateStatus; }
    public void setGateStatus(String gateStatus) { this.gateStatus = gateStatus; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<String> getCriticalIssues() { return criticalIssues; }
    public void setCriticalIssues(List<String> criticalIssues) {
        this.criticalIssues = immutable(criticalIssues);
    }
    public List<ResearchEvaluationMetric> getMetrics() { return metrics; }
    public void setMetrics(List<ResearchEvaluationMetric> metrics) {
        this.metrics = metrics == null || metrics.isEmpty()
                ? Collections.<ResearchEvaluationMetric>emptyList()
                : Collections.unmodifiableList(new ArrayList<ResearchEvaluationMetric>(metrics));
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    private List<String> immutable(List<String> values) {
        return values == null || values.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
