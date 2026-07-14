package com.finscope.domain.instrument;

import com.finscope.domain.marketdata.MarketDataQualityStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行情快照：某标的在某个时刻的价格与涨跌。
 */
@Data
public class Quote {
    /**
     * 标的代码。
     */
    private String instrumentCode;
    /**
     * 名称。
     */
    private String name;
    /**
     * 最新价格。
     */
    private Double price;
    /**
     * 基金最近确认的单位净值。
     */
    private Double confirmedNav;
    /**
     * 确认净值对应日期。
     */
    private String confirmedNavDate;
    /**
     * 确认净值涨跌幅百分比。
     */
    private Double confirmedNavChangePct;
    /**
     * 昨收价。
     */
    private Double previousClose;
    /**
     * 涨跌幅百分比。
     */
    private Double changePct;
    /**
     * 涨跌额。
     */
    private Double changeAmount;
    /**
     * 换手率或成交额。
     */
    private Double turnover;
    /**
     * 成交量。
     */
    private Double volume;
    /**
     * 开盘价。
     */
    private Double open;
    /**
     * 最高价。
     */
    private Double high;
    /**
     * 最低价。
     */
    private Double low;
    /**
     * 振幅。
     */
    private Double amplitude;
    /**
     * 行情时间。
     */
    private LocalDateTime quoteTime;
    /**
     * 是否取到有效行情。
     */
    private boolean valid = true;
    /**
     * 备注信息。
     */
    private String note;
    /**
     * 数据质量状态。
     */
    private MarketDataQualityStatus qualityStatus;
    /**
     * 实际返回该条数据的数据提供方编码。
     */
    private String sourceCode;
    /**
     * 数据对应时间。
     */
    private LocalDateTime asOf;
    /**
     * 数据拉取时间。
     */
    private LocalDateTime retrievedAt;
    /**
     * 使用旧快照时距当前的秒数。
     */
    private Long staleAgeSeconds;
    /**
     * 面向用户的降级说明。
     */
    private String warning;
    /**
     * 本次刷新链路标识。
     */
    private String refreshId;
}
