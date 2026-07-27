package com.finscope.service.research.mission;

public class ResearchPlanPatch {
    private String operation;
    private String taskKey;
    private String title;
    private String question;
    private String toolCode;
    private String intent;
    private String queryText;
    private String reason;

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String taskKey) { this.taskKey = taskKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getToolCode() { return toolCode; }
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
