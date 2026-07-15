package com.finscope.domain.quant.data;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class QuantDataset {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 名称。
     */
    private String name;
    /**
     * 交易市场。
     */
    private String market;
    /**
     * 股票池类型。
     */
    private String universeType;
    /**
     * 信息源类型。
     */
    private String sourceType;
    /**
     * 数据类型。
     */
    private String dataKind;
    /**
     * Dataset usage level, such as RESEARCH or LEARNING.
     */
    private String datasetLevel;
    /**
     * Latest information time included in the frozen dataset.
     */
    private LocalDateTime asOfTime;
    /**
     * Version of the dataset fingerprint protocol.
     */
    private String fingerprintVersion;
    /**
     * Serialized manifest of frozen dataset partitions.
     */
    private String partitionManifest;
    /**
     * 开始日期。
     */
    private LocalDate startDate;
    /**
     * 结束日期。
     */
    private LocalDate endDate;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 内容指纹。
     */
    private String fingerprint;
    /**
     * 数据质量摘要。
     */
    private String qualitySummary;
    /**
     * 数据版本号，用于并发更新校验。
     */
    private long revision;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
}
