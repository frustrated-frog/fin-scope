package com.finscope.service.research.mission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchMissionDraft {
    private String researchType;
    private List<String> methodCodes = new ArrayList<String>();
    private List<String> requiredEvidence = new ArrayList<String>();
    private List<String> requiredCalculations = new ArrayList<String>();
    private List<String> counterChecks = new ArrayList<String>();
    private List<String> completionCriteria = new ArrayList<String>();
    private String scopeSummary;
    private List<String> successCriteria = new ArrayList<String>();
    private List<ResearchMissionTaskDraft> tasks = new ArrayList<ResearchMissionTaskDraft>();

    public String getResearchType() { return researchType; }
    public void setResearchType(String researchType) { this.researchType = researchType; }
    public List<String> getMethodCodes() { return methodCodes; }
    public void setMethodCodes(List<String> values) { this.methodCodes = copy(values); }
    public List<String> getRequiredEvidence() { return requiredEvidence; }
    public void setRequiredEvidence(List<String> values) { this.requiredEvidence = copy(values); }
    public List<String> getRequiredCalculations() { return requiredCalculations; }
    public void setRequiredCalculations(List<String> values) { this.requiredCalculations = copy(values); }
    public List<String> getCounterChecks() { return counterChecks; }
    public void setCounterChecks(List<String> values) { this.counterChecks = copy(values); }
    public List<String> getCompletionCriteria() { return completionCriteria; }
    public void setCompletionCriteria(List<String> values) { this.completionCriteria = copy(values); }

    public String getScopeSummary() {
        return scopeSummary;
    }

    public void setScopeSummary(String scopeSummary) {
        this.scopeSummary = scopeSummary;
    }

    public List<String> getSuccessCriteria() {
        return successCriteria;
    }

    public void setSuccessCriteria(List<String> successCriteria) {
        this.successCriteria = successCriteria == null
                ? new ArrayList<String>()
                : new ArrayList<String>(successCriteria);
    }

    public List<ResearchMissionTaskDraft> getTasks() {
        return tasks;
    }

    public void setTasks(List<ResearchMissionTaskDraft> tasks) {
        this.tasks = tasks == null
                ? new ArrayList<ResearchMissionTaskDraft>()
                : new ArrayList<ResearchMissionTaskDraft>(tasks);
    }

    public ResearchMissionTaskDraft task(String taskKey) {
        for (ResearchMissionTaskDraft task : tasks) {
            if (taskKey != null && taskKey.equals(task.getTaskKey())) {
                return task;
            }
        }
        throw new IllegalArgumentException("研究任务不存在：" + taskKey);
    }

    public List<ResearchMissionTaskDraft> immutableTasks() {
        return Collections.unmodifiableList(new ArrayList<ResearchMissionTaskDraft>(tasks));
    }

    private static List<String> copy(List<String> values) {
        return values == null ? new ArrayList<String>() : new ArrayList<String>(values);
    }
}
