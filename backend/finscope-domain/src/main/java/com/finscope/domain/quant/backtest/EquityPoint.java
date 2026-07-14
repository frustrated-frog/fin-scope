package com.finscope.domain.quant.backtest;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EquityPoint {
    private LocalDate tradeDate;
    private double portfolioNav;
    private double benchmarkNav;
    private double cash;
    private double totalAsset;
    private double drawdown;
}
