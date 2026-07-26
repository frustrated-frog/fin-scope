package com.finscope.domain.research.agent;

public class ResearchAgentTrajectoryMetrics {
    private int decisionCount;
    private int observationCount;
    private double decisionValidityRate;
    private double observationFollowupRate;
    private double duplicateActionRate;
    private double noProgressRate;
    private double replanSuccessRate;
    private double finishFirstPassRate;
    private double fallbackRate;
    private int qualityScore;

    public int getDecisionCount() { return decisionCount; }
    public void setDecisionCount(int decisionCount) { this.decisionCount = decisionCount; }
    public int getObservationCount() { return observationCount; }
    public void setObservationCount(int observationCount) { this.observationCount = observationCount; }
    public double getDecisionValidityRate() { return decisionValidityRate; }
    public void setDecisionValidityRate(double decisionValidityRate) { this.decisionValidityRate = decisionValidityRate; }
    public double getObservationFollowupRate() { return observationFollowupRate; }
    public void setObservationFollowupRate(double observationFollowupRate) { this.observationFollowupRate = observationFollowupRate; }
    public double getDuplicateActionRate() { return duplicateActionRate; }
    public void setDuplicateActionRate(double duplicateActionRate) { this.duplicateActionRate = duplicateActionRate; }
    public double getNoProgressRate() { return noProgressRate; }
    public void setNoProgressRate(double noProgressRate) { this.noProgressRate = noProgressRate; }
    public double getReplanSuccessRate() { return replanSuccessRate; }
    public void setReplanSuccessRate(double replanSuccessRate) { this.replanSuccessRate = replanSuccessRate; }
    public double getFinishFirstPassRate() { return finishFirstPassRate; }
    public void setFinishFirstPassRate(double finishFirstPassRate) { this.finishFirstPassRate = finishFirstPassRate; }
    public double getFallbackRate() { return fallbackRate; }
    public void setFallbackRate(double fallbackRate) { this.fallbackRate = fallbackRate; }
    public int getQualityScore() { return qualityScore; }
    public void setQualityScore(int qualityScore) { this.qualityScore = qualityScore; }
}
