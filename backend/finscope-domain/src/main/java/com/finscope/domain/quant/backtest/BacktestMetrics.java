package com.finscope.domain.quant.backtest;

public class BacktestMetrics {
    private double totalReturn; private double annualizedReturn; private double annualizedVolatility;
    private double maxDrawdown; private double sharpeRatio; private double calmarRatio;
    private double winRate; private double turnover; private int tradeCount; private double benchmarkReturn; private double excessReturn;
    public double getTotalReturn() { return totalReturn; }
    public void setTotalReturn(double totalReturn) { this.totalReturn = totalReturn; }
    public double getAnnualizedReturn() { return annualizedReturn; }
    public void setAnnualizedReturn(double annualizedReturn) { this.annualizedReturn = annualizedReturn; }
    public double getAnnualizedVolatility() { return annualizedVolatility; }
    public void setAnnualizedVolatility(double annualizedVolatility) { this.annualizedVolatility = annualizedVolatility; }
    public double getMaxDrawdown() { return maxDrawdown; }
    public void setMaxDrawdown(double maxDrawdown) { this.maxDrawdown = maxDrawdown; }
    public double getSharpeRatio() { return sharpeRatio; }
    public void setSharpeRatio(double sharpeRatio) { this.sharpeRatio = sharpeRatio; }
    public double getCalmarRatio() { return calmarRatio; }
    public void setCalmarRatio(double calmarRatio) { this.calmarRatio = calmarRatio; }
    public double getWinRate() { return winRate; }
    public void setWinRate(double winRate) { this.winRate = winRate; }
    public double getTurnover() { return turnover; }
    public void setTurnover(double turnover) { this.turnover = turnover; }
    public int getTradeCount() { return tradeCount; }
    public void setTradeCount(int tradeCount) { this.tradeCount = tradeCount; }
    public double getBenchmarkReturn() { return benchmarkReturn; }
    public void setBenchmarkReturn(double benchmarkReturn) { this.benchmarkReturn = benchmarkReturn; }
    public double getExcessReturn() { return excessReturn; }
    public void setExcessReturn(double excessReturn) { this.excessReturn = excessReturn; }
}
