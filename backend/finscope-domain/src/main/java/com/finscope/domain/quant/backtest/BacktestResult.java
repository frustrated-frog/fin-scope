package com.finscope.domain.quant.backtest;

import java.util.ArrayList;
import java.util.List;

public class BacktestResult {
    private List<EquityPoint> equityCurve = new ArrayList<EquityPoint>();
    private List<BacktestTrade> trades = new ArrayList<BacktestTrade>();
    private List<String> warnings = new ArrayList<String>();
    private List<AnnualPerformance> annualPerformance = new ArrayList<AnnualPerformance>();
    private BacktestMetrics metrics = new BacktestMetrics();
    public List<EquityPoint> getEquityCurve() { return equityCurve; }
    public void setEquityCurve(List<EquityPoint> equityCurve) { this.equityCurve = equityCurve; }
    public List<BacktestTrade> getTrades() { return trades; }
    public void setTrades(List<BacktestTrade> trades) { this.trades = trades; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public List<AnnualPerformance> getAnnualPerformance() { return annualPerformance; }
    public void setAnnualPerformance(List<AnnualPerformance> annualPerformance) { this.annualPerformance = annualPerformance; }
    public BacktestMetrics getMetrics() { return metrics; }
    public void setMetrics(BacktestMetrics metrics) { this.metrics = metrics; }
}
