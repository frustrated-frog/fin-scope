package com.finscope.domain.quant.data;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class QuantFundamentalSnapshot {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 数据集 ID。
     */
    private Long datasetId;
    /**
     * 标的代码。
     */
    private String instrumentCode;
    /**
     * 报告期。
     */
    private LocalDate reportPeriod;
    /**
     * 披露时间。
     */
    private LocalDate disclosedAt;
    /**
     * 市盈率 PE。
     */
    private BigDecimal pe;
    /**
     * 市净率 PB。
     */
    private BigDecimal pb;
    /**
     * 总市值。
     */
    private BigDecimal marketCap;
    /**
     * 净资产收益率 ROE。
     */
    private BigDecimal roe;
    /**
     * 收入增速。
     */
    private BigDecimal revenueGrowth;
    /**
     * 利润增速。
     */
    private BigDecimal profitGrowth;
    /**
     * 负债率。
     */
    private BigDecimal debtRatio;
}
