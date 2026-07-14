package com.finscope.domain.quant.strategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class QuantStrategyDraft {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 数据集 ID。
     */
    private Long datasetId;
    /**
     * 策略生成提示词。
     */
    private String prompt;
    /**
     * 原始响应。
     */
    private String rawResponse;
    /**
     * 标准化策略规格。
     */
    private String normalizedSpec;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 模型名称。
     */
    private String model;
    /**
     * 已校验数据集指纹。
     */
    private String validatedDatasetFingerprint;
    /**
     * 策略规格。
     */
    private QuantStrategySpec spec;
    /**
     * 校验问题列表。
     */
    private List<String> validationIssues = new ArrayList<String>();
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }
    public String getNormalizedSpec() { return normalizedSpec; }
    public void setNormalizedSpec(String normalizedSpec) { this.normalizedSpec = normalizedSpec; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getValidatedDatasetFingerprint() { return validatedDatasetFingerprint; }
    public void setValidatedDatasetFingerprint(String validatedDatasetFingerprint) { this.validatedDatasetFingerprint = validatedDatasetFingerprint; }
    public QuantStrategySpec getSpec() { return spec; }
    public void setSpec(QuantStrategySpec spec) { this.spec = spec; }
    public List<String> getValidationIssues() { return validationIssues; }
    public void setValidationIssues(List<String> validationIssues) { this.validationIssues = validationIssues; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
