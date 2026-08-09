package com.finscope.domain.learningcard;

public class StockLearningCardClaim {
    private Long id;
    private Long runId;
    private String dimensionCode;
    private String status;
    private String failureMessage;
    private String judgment;
    private String rationale;
    private String counterargument;
    private String unknowns;
    private String confidence;
    private int sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getDimensionCode() { return dimensionCode; }
    public void setDimensionCode(String dimensionCode) { this.dimensionCode = dimensionCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public String getJudgment() { return judgment; }
    public void setJudgment(String judgment) { this.judgment = judgment; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getCounterargument() { return counterargument; }
    public void setCounterargument(String counterargument) { this.counterargument = counterargument; }
    public String getUnknowns() { return unknowns; }
    public void setUnknowns(String unknowns) { this.unknowns = unknowns; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
