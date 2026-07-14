package com.finscope.domain.quant.data;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class QuantDailyBar {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 数据集 ID。
     */
    private Long datasetId;
    /**
     * 交易日期。
     */
    private LocalDate tradeDate;
    /**
     * 标的代码。
     */
    private String instrumentCode;
    /**
     * 开盘价。
     */
    private BigDecimal open;
    /**
     * 最高价。
     */
    private BigDecimal high;
    /**
     * 最低价。
     */
    private BigDecimal low;
    /**
     * 收盘价。
     */
    private BigDecimal close;
    /**
     * 复权收盘价。
     */
    private BigDecimal adjustedClose;
    /**
     * 成交量。
     */
    private BigDecimal volume;
    /**
     * 成交额。
     */
    private BigDecimal amount;
    /**
     * 交易状态。
     */
    private String tradeStatus;
    /**
     * 是否为 ST 股票。
     */
    private boolean st;
    /**
     * 涨停价。
     */
    private boolean limitUp;
    /**
     * 跌停价。
     */
    private boolean limitDown;
}
