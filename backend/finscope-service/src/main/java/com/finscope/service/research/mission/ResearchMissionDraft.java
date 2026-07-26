package com.finscope.service.research.mission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchMissionDraft {
    private String scopeSummary;
    private List<String> successCriteria = new ArrayList<String>();
    private List<ResearchMissionTaskDraft> tasks = new ArrayList<ResearchMissionTaskDraft>();

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
}
