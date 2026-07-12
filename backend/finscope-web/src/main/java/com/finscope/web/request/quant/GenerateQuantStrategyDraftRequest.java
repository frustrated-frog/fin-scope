package com.finscope.web.request.quant;

public class GenerateQuantStrategyDraftRequest {
    private Long datasetId;
    private String prompt;
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
}
