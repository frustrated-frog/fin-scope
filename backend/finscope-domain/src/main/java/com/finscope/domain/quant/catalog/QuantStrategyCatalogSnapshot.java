package com.finscope.domain.quant.catalog;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class QuantStrategyCatalogSnapshot {
    private String sourceCode;
    private String repositoryUrl;
    private String branch;
    private String commitSha;
    private LocalDateTime fetchedAt;
    private List<QuantStrategyCatalogEntry> entries = new ArrayList<QuantStrategyCatalogEntry>();

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
    public List<QuantStrategyCatalogEntry> getEntries() { return entries; }
    public void setEntries(List<QuantStrategyCatalogEntry> entries) { this.entries = entries; }
}
