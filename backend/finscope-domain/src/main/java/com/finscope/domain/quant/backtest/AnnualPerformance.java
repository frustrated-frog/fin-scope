package com.finscope.domain.quant.backtest;

public class AnnualPerformance {
    private int year; private double portfolioReturn; private double benchmarkReturn; private double excessReturn; private double maxDrawdown;
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public double getPortfolioReturn() { return portfolioReturn; }
    public void setPortfolioReturn(double portfolioReturn) { this.portfolioReturn = portfolioReturn; }
    public double getBenchmarkReturn() { return benchmarkReturn; }
    public void setBenchmarkReturn(double benchmarkReturn) { this.benchmarkReturn = benchmarkReturn; }
    public double getExcessReturn() { return excessReturn; }
    public void setExcessReturn(double excessReturn) { this.excessReturn = excessReturn; }
    public double getMaxDrawdown() { return maxDrawdown; }
    public void setMaxDrawdown(double maxDrawdown) { this.maxDrawdown = maxDrawdown; }
}
