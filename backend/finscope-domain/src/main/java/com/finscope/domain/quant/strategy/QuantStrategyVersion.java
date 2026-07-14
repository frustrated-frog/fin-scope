package com.finscope.domain.quant.strategy;

import java.time.LocalDateTime;

public class QuantStrategyVersion {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 名称。
     */
    private String name;
    /**
     * 数据集 ID。
     */
    private Long datasetId;
    /**
     * 版本。
     */
    private int version;
    /**
     * 策略规格 JSON。
     */
    private String specJson;
    /**
     * 策略内容指纹。
     */
    private String strategyFingerprint;
    /**
     * 数据集指纹。
     */
    private String datasetFingerprint;
    /**
     * 引擎版本。
     */
    private String engineVersion;
    /**
     * 来源说明。
     */
    private String source;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getSpecJson() { return specJson; }
    public void setSpecJson(String specJson) { this.specJson = specJson; }
    public String getStrategyFingerprint() { return strategyFingerprint; }
    public void setStrategyFingerprint(String strategyFingerprint) { this.strategyFingerprint = strategyFingerprint; }
    public String getDatasetFingerprint() { return datasetFingerprint; }
    public void setDatasetFingerprint(String datasetFingerprint) { this.datasetFingerprint = datasetFingerprint; }
    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
