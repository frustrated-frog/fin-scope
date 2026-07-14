package com.finscope.domain.quant.backtest;

import lombok.Data;

@Data
public class BacktestMetrics {
    /**
     * 总收益率。
     */
    private double totalReturn;
    /**
     * 年化收益率。
     */
    private double annualizedReturn;
    /**
     * 年化波动率。
     */
    private double annualizedVolatility;
    /**
     * 最大回撤。
     */
    private double maxDrawdown;
    /**
     * 夏普比率。
     */
    private double sharpeRatio;
    /**
     * 卡玛比率。
     */
    private double calmarRatio;
    /**
     * 胜率。
     */
    private double winRate;
    /**
     * 换手率或成交额。
     */
    private double turnover;
    /**
     * 交易次数。
     */
    private int tradeCount;
    /**
     * 基准收益率。
     */
    private double benchmarkReturn;
    /**
     * 超额收益率。
     */
    private double excessReturn;
}
