package com.finscope.domain.agent;

import java.time.LocalDateTime;

public class AgentRun {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 研究运行 ID。
     */
    private Long researchRunId;
    /**
     * 事件 ID。
     */
    private Long eventId;
    /**
     * 文章 ID。
     */
    private Long articleId;
    /**
     * 节点名称。
     */
    private String nodeName;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 输入内容。
     */
    private String input;
    /**
     * 输出内容。
     */
    private String output;
    /**
     * 错误信息。
     */
    private String errorMessage;
    /**
     * 耗时毫秒数。
     */
    private long durationMs;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 步骤 ID。
     */
    private String stepId;
    /**
     * 尝试次数。
     */
    private int attempt = 1;
    /**
     * 动作指纹。
     */
    private String actionFingerprint;
    /**
     * 输入内容哈希。
     */
    private String inputHash;
    /**
     * 输出内容哈希。
     */
    private String outputHash;
    /**
     * 错误类型。
     */
    private String errorType;
    /**
     * 是否使用兜底结果。
     */
    private boolean fallbackUsed;
    /**
     * 兜底原因。
     */
    private String fallbackReason;
    /**
     * 终止原因。
     */
    private String terminationReason;
    /**
     * 进度增量。
     */
    private int progressDelta;
    /**
     * 预算快照。
     */
    private String budgetSnapshot;
    /**
     * 扩展元数据 JSON。
     */
    private String metadataJson;
    /**
     * 研究对象类型。
     */
    private String subjectType;
    /**
     * 对象 ID。
     */
    private Long subjectId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getResearchRunId() {
        return researchRunId;
    }

    public void setResearchRunId(Long researchRunId) {
        this.researchRunId = researchRunId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public String getActionFingerprint() {
        return actionFingerprint;
    }

    public void setActionFingerprint(String actionFingerprint) {
        this.actionFingerprint = actionFingerprint;
    }

    public String getInputHash() {
        return inputHash;
    }

    public void setInputHash(String inputHash) {
        this.inputHash = inputHash;
    }

    public String getOutputHash() {
        return outputHash;
    }

    public void setOutputHash(String outputHash) {
        this.outputHash = outputHash;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public String getTerminationReason() {
        return terminationReason;
    }

    public void setTerminationReason(String terminationReason) {
        this.terminationReason = terminationReason;
    }

    public int getProgressDelta() {
        return progressDelta;
    }

    public void setProgressDelta(int progressDelta) {
        this.progressDelta = progressDelta;
    }

    public String getBudgetSnapshot() {
        return budgetSnapshot;
    }

    public void setBudgetSnapshot(String budgetSnapshot) {
        this.budgetSnapshot = budgetSnapshot;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
}
