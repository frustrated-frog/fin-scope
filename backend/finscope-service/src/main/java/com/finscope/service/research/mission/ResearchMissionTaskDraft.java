package com.finscope.service.research.mission;

import com.finscope.domain.research.mission.ResearchMissionTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchMissionTaskDraft {
    private String taskKey;
    private String title;
    private String question;
    private String taskType;
    private String toolCode;
    private String intent;
    private List<String> dependencies = new ArrayList<String>();
    private String parallelGroup;
    private String queryText;
    private String rationale;
    private String expectedEvidence;

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getToolCode() {
        return toolCode;
    }

    public void setToolCode(String toolCode) {
        this.toolCode = toolCode;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies == null
                ? new ArrayList<String>()
                : new ArrayList<String>(dependencies);
    }

    public String getParallelGroup() {
        return parallelGroup;
    }

    public void setParallelGroup(String parallelGroup) {
        this.parallelGroup = parallelGroup;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public String getExpectedEvidence() {
        return expectedEvidence;
    }

    public void setExpectedEvidence(String expectedEvidence) {
        this.expectedEvidence = expectedEvidence;
    }

    public ResearchMissionTask toDomain() {
        ResearchMissionTask value = new ResearchMissionTask();
        value.setTaskKey(taskKey);
        value.setTitle(title);
        value.setQuestion(question);
        value.setTaskType(taskType);
        value.setToolCode(toolCode);
        value.setIntent(intent);
        value.setDependencies(dependencies);
        value.setParallelGroup(parallelGroup);
        value.setQueryText(queryText);
        value.setRationale(rationale);
        value.setExpectedEvidence(expectedEvidence);
        return value;
    }

    public List<String> immutableDependencies() {
        return Collections.unmodifiableList(new ArrayList<String>(dependencies));
    }
}
