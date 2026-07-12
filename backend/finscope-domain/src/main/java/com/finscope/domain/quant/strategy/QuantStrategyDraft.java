package com.finscope.domain.quant.strategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class QuantStrategyDraft {
    private Long id;
    private Long datasetId;
    private String prompt;
    private String rawResponse;
    private String normalizedSpec;
    private String status;
    private String model;
    private QuantStrategySpec spec;
    private List<String> validationIssues = new ArrayList<String>();
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
    public QuantStrategySpec getSpec() { return spec; }
    public void setSpec(QuantStrategySpec spec) { this.spec = spec; }
    public List<String> getValidationIssues() { return validationIssues; }
    public void setValidationIssues(List<String> validationIssues) { this.validationIssues = validationIssues; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
