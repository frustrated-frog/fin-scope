package com.finscope.domain.quant.backtest;

import java.time.LocalDate;

public class EquityPoint {
    private LocalDate tradeDate; private double portfolioNav; private double benchmarkNav;
    private double cash; private double totalAsset; private double drawdown;
    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }
    public double getPortfolioNav() { return portfolioNav; }
    public void setPortfolioNav(double portfolioNav) { this.portfolioNav = portfolioNav; }
    public double getBenchmarkNav() { return benchmarkNav; }
    public void setBenchmarkNav(double benchmarkNav) { this.benchmarkNav = benchmarkNav; }
    public double getCash() { return cash; }
    public void setCash(double cash) { this.cash = cash; }
    public double getTotalAsset() { return totalAsset; }
    public void setTotalAsset(double totalAsset) { this.totalAsset = totalAsset; }
    public double getDrawdown() { return drawdown; }
    public void setDrawdown(double drawdown) { this.drawdown = drawdown; }
}
