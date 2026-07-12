package com.finscope.domain.quant.experiment;

import com.finscope.domain.quant.backtest.BacktestResult;
import java.time.LocalDateTime;

public class QuantExperiment {
    private Long id; private Long strategyVersionId; private String requestFingerprint;
    private String datasetFingerprint; private String engineVersion; private String status; private String errorMessage;
    private LocalDateTime createdAt; private LocalDateTime startedAt; private LocalDateTime completedAt;
    private BacktestResult result; private String interpretation;
    private Long datasetId; private String datasetName; private String dataKind;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStrategyVersionId() { return strategyVersionId; }
    public void setStrategyVersionId(Long strategyVersionId) { this.strategyVersionId = strategyVersionId; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }
    public String getDatasetFingerprint() { return datasetFingerprint; }
    public void setDatasetFingerprint(String datasetFingerprint) { this.datasetFingerprint = datasetFingerprint; }
    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public BacktestResult getResult() { return result; }
    public void setResult(BacktestResult result) { this.result = result; }
    public String getInterpretation() { return interpretation; }
    public void setInterpretation(String interpretation) { this.interpretation = interpretation; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }
    public String getDataKind() { return dataKind; }
    public void setDataKind(String dataKind) { this.dataKind = dataKind; }
}
