package com.finscope.domain.quant.backtest;

import lombok.Data;

@Data
public class BacktestMetrics {
    private double totalReturn;
    private double annualizedReturn;
    private double annualizedVolatility;
    private double maxDrawdown;
    private double sharpeRatio;
    private double calmarRatio;
    private double winRate;
    private double turnover;
    private int tradeCount;
    private double benchmarkReturn;
    private double excessReturn;
}
