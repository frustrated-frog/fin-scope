package com.finscope.domain.quant.backtest;

import lombok.Data;

@Data
public class AnnualPerformance {
    private int year;
    private double portfolioReturn;
    private double benchmarkReturn;
    private double excessReturn;
    private double maxDrawdown;
}
