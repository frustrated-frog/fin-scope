package com.finscope.domain.research;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchRunPlanStep {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 研究运行 ID。
     */
    private Long researchRunId;
    /**
     * 步骤 ID。
     */
    private String stepId;
    /**
     * 标题。
     */
    private String title;
    /**
     * 步骤类型。
     */
    private String stepType;
    /**
     * 执行器名称。
     */
    private String executor;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 依赖步骤列表。
     */
    private List<String> dependencies = Collections.emptyList();
    /**
     * 输入摘要。
     */
    private String inputSummary;
    /**
     * 输出摘要。
     */
    private String outputSummary;
    /**
     * 错误类型。
     */
    private String errorType;
    /**
     * 错误信息。
     */
    private String errorMessage;
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
     * 尝试次数。
     */
    private int attempt;
    /**
     * 最大尝试次数。
     */
    private int maxAttempts = 1;
    /**
     * 进度增量。
     */
    private int progressDelta;
    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 结束时间。
     */
    private LocalDateTime endedAt;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
    /**
     * 扩展元数据 JSON。
     */
    private String metadataJson;

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

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies == null || dependencies.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(dependencies));
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public void setInputSummary(String inputSummary) {
        this.inputSummary = inputSummary;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public void setOutputSummary(String outputSummary) {
        this.outputSummary = outputSummary;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getProgressDelta() {
        return progressDelta;
    }

    public void setProgressDelta(int progressDelta) {
        this.progressDelta = progressDelta;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }
}
