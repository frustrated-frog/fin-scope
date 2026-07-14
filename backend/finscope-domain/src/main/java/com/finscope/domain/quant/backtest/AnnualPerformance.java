package com.finscope.domain.quant.backtest;

import lombok.Data;

@Data
public class AnnualPerformance {
    /**
     * 年份。
     */
    private int year;
    /**
     * 组合收益率。
     */
    private double portfolioReturn;
    /**
     * 基准收益率。
     */
    private double benchmarkReturn;
    /**
     * 超额收益率。
     */
    private double excessReturn;
    /**
     * 最大回撤。
     */
    private double maxDrawdown;
}
