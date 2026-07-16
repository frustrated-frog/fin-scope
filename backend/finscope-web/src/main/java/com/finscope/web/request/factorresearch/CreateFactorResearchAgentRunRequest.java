package com.finscope.web.request.factorresearch;

import com.finscope.domain.factorresearch.FactorIdentity;

public class CreateFactorResearchAgentRunRequest {
    private Long datasetId;
    private String factorNamespace;
    private String factorCode;
    private String factorVersion;
    private Long researchDraftId;
    private String question;

    public Long getDatasetId() { return datasetId; } public void setDatasetId(Long value) { datasetId = value; }
    public String getFactorNamespace() { return factorNamespace; } public void setFactorNamespace(String value) { factorNamespace = value; }
    public String getFactorCode() { return factorCode; } public void setFactorCode(String value) { factorCode = value; }
    public String getFactorVersion() { return factorVersion; } public void setFactorVersion(String value) { factorVersion = value; }
    public Long getResearchDraftId() { return researchDraftId; } public void setResearchDraftId(Long value) { researchDraftId = value; }
    public String getQuestion() { return question; } public void setQuestion(String value) { question = value; }
    public FactorIdentity factor() { return new FactorIdentity(factorNamespace, factorCode, factorVersion); }
}
