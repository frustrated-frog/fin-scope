package com.finscope.domain.quant.experiment;

import com.finscope.domain.quant.backtest.BacktestResult;
import java.time.LocalDateTime;

public class QuantExperiment {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 策略版本 ID。
     */
    private Long strategyVersionId;
    /**
     * 请求指纹。
     */
    private String requestFingerprint;
    /**
     * 数据集指纹。
     */
    private String datasetFingerprint;
    /**
     * 引擎版本。
     */
    private String engineVersion;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 错误信息。
     */
    private String errorMessage;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 完成时间。
     */
    private LocalDateTime completedAt;
    /**
     * 结果对象。
     */
    private BacktestResult result;
    /**
     * 解读内容。
     */
    private String interpretation;
    /**
     * 数据集 ID。
     */
    private Long datasetId;
    /**
     * 数据集名称。
     */
    private String datasetName;
    /**
     * 数据类型。
     */
    private String dataKind;
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
