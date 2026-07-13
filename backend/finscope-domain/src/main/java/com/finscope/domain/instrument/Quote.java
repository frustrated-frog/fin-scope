package com.finscope.domain.instrument;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行情快照：某标的在某个时刻的价格与涨跌。
 */
@Data
public class Quote {
    private String instrumentCode;
    private String name;
    /** 最新价 / 基金估值净值 */
    private Double price;
    /** 基金最近确认的单位净值 */
    private Double confirmedNav;
    /** 确认净值对应日期，格式 yyyy-MM-dd */
    private String confirmedNavDate;
    private Double confirmedNavChangePct;
    /** 昨收 */
    private Double previousClose;
    /** 涨跌幅（百分比，如 -3.1 表示 -3.1%） */
    private Double changePct;
    /** 涨跌额 */
    private Double changeAmount;
    /** 成交额（元） */
    private Double turnover;
    /** 成交量 */
    private Double volume;
    /** 今开 */
    private Double open;
    /** 当日最高 */
    private Double high;
    /** 当日最低 */
    private Double low;
    /** 振幅（百分比，(最高-最低)/昨收*100） */
    private Double amplitude;
    /** 行情时间 */
    private LocalDateTime quoteTime;
    /** 是否取到有效行情 */
    private boolean valid = true;
    /** 取数失败或估值等提示信息 */
    private String note;
}
