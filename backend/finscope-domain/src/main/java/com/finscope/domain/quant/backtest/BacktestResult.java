package com.finscope.domain.quant.backtest;

import java.util.ArrayList;
import java.util.List;

public class BacktestResult {
    /**
     * 权益曲线。
     */
    private List<EquityPoint> equityCurve = new ArrayList<EquityPoint>();
    /**
     * 交易记录列表。
     */
    private List<BacktestTrade> trades = new ArrayList<BacktestTrade>();
    /**
     * 警告列表。
     */
    private List<String> warnings = new ArrayList<String>();
    /**
     * 年度表现列表。
     */
    private List<AnnualPerformance> annualPerformance = new ArrayList<AnnualPerformance>();
    /**
     * 持仓快照列表。
     */
    private List<PositionSnapshot> positions = new ArrayList<PositionSnapshot>();
    /**
     * 回测指标。
     */
    private BacktestMetrics metrics = new BacktestMetrics();
    public List<EquityPoint> getEquityCurve() { return equityCurve; }
    public void setEquityCurve(List<EquityPoint> equityCurve) { this.equityCurve = equityCurve; }
    public List<BacktestTrade> getTrades() { return trades; }
    public void setTrades(List<BacktestTrade> trades) { this.trades = trades; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public List<AnnualPerformance> getAnnualPerformance() { return annualPerformance; }
    public void setAnnualPerformance(List<AnnualPerformance> annualPerformance) { this.annualPerformance = annualPerformance; }
    public List<PositionSnapshot> getPositions() { return positions; }
    public void setPositions(List<PositionSnapshot> positions) { this.positions = positions; }
    public BacktestMetrics getMetrics() { return metrics; }
    public void setMetrics(BacktestMetrics metrics) { this.metrics = metrics; }
}
