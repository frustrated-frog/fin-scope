package com.finscope.domain.quant.backtest;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EquityPoint {
    /**
     * 交易日期。
     */
    private LocalDate tradeDate;
    /**
     * 组合净值。
     */
    private double portfolioNav;
    /**
     * 基准净值。
     */
    private double benchmarkNav;
    /**
     * 现金余额。
     */
    private double cash;
    /**
     * 总资产。
     */
    private double totalAsset;
    /**
     * 回撤。
     */
    private double drawdown;
}
