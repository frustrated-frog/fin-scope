package com.finscope.domain.marketintel;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CapitalFlowPoint {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 标的 ID。
     */
    private Long instrumentId;
    /**
     * 数据提供方编码。
     */
    private String providerCode;
    /**
     * 数据粒度。
     */
    private String granularity;
    /**
     * 数据日期。
     */
    private LocalDate dataDate;
    /**
     * 观测时间。
     */
    private LocalDateTime observedAt;
    /**
     * 最新价格。
     */
    private BigDecimal price;
    /**
     * 成交量。
     */
    private BigDecimal tradeVolume;
    /**
     * 区间成交额。
     */
    private BigDecimal intervalTradeAmount;
    /**
     * 累计成交额。
     */
    private BigDecimal cumulativeTradeAmount;
    /**
     * 换手率。
     */
    private BigDecimal turnoverRate;
    /**
     * 量比。
     */
    private BigDecimal volumeRatio;
    /**
     * 主力流入。
     */
    private BigDecimal mainInflow;
    /**
     * 主力流出。
     */
    private BigDecimal mainOutflow;
    /**
     * 主力净流入。
     */
    private BigDecimal mainNetInflow;
    /**
     * 超大单净流入。
     */
    private BigDecimal superLargeNetInflow;
    /**
     * 大单净流入。
     */
    private BigDecimal largeNetInflow;
    /**
     * 中单净流入。
     */
    private BigDecimal mediumNetInflow;
    /**
     * 小单净流入。
     */
    private BigDecimal smallNetInflow;
    /**
     * 计算版本。
     */
    private String calculationVersion;
    /**
     * 数据拉取时间。
     */
    private LocalDateTime retrievedAt;
    /**
     * 原始载荷哈希。
     */
    private String payloadHash;
    /**
     * 数据质量状态。
     */
    private String qualityStatus;

    public String metricRef(String metric) {
        if (id == null) {
            throw new IllegalStateException("metric reference requires a persisted flow point");
        }
        return "flow:" + id + ":" + metric;
    }
}
