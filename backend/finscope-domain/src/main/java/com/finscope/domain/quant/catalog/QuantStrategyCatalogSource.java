package com.finscope.domain.quant.catalog;

import java.time.LocalDateTime;

public class QuantStrategyCatalogSource {
    private String code;
    private String repositoryUrl;
    private String branch;
    private String commitSha;
    private String status;
    private LocalDateTime lastSyncedAt;
    private String errorMessage;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(LocalDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
