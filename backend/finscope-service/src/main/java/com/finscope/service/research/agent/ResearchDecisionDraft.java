package com.finscope.service.research.agent;

import java.util.LinkedHashMap;
import java.util.Map;

public class ResearchDecisionDraft {
    private String decisionType;
    private String currentSubgoal;
    private String missionTaskKey;
    private String toolCode;
    private Map<String, Object> arguments = new LinkedHashMap<String, Object>();
    private String targetGap;
    private String expectedObservation;
    private String decisionSummary;
    private double confidence;
    private Map<String, Object> planPatch = new LinkedHashMap<String, Object>();

    public String getDecisionType() { return decisionType; }
    public void setDecisionType(String decisionType) { this.decisionType = decisionType; }
    public String getCurrentSubgoal() { return currentSubgoal; }
    public void setCurrentSubgoal(String currentSubgoal) { this.currentSubgoal = currentSubgoal; }
    public String getMissionTaskKey() { return missionTaskKey; }
    public void setMissionTaskKey(String missionTaskKey) { this.missionTaskKey = missionTaskKey; }
    public String getToolCode() { return toolCode; }
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
    public Map<String, Object> getArguments() { return arguments; }
    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(arguments);
    }
    public String getTargetGap() { return targetGap; }
    public void setTargetGap(String targetGap) { this.targetGap = targetGap; }
    public String getExpectedObservation() { return expectedObservation; }
    public void setExpectedObservation(String expectedObservation) { this.expectedObservation = expectedObservation; }
    public String getDecisionSummary() { return decisionSummary; }
    public void setDecisionSummary(String decisionSummary) { this.decisionSummary = decisionSummary; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public Map<String, Object> getPlanPatch() { return planPatch; }
    public void setPlanPatch(Map<String, Object> planPatch) {
        this.planPatch = planPatch == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(planPatch);
    }
}
