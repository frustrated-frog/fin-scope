package com.finscope.domain.quant.backtest;

import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import java.util.ArrayList;
import java.util.List;

public class BacktestRequest {
    private QuantStrategySpec spec;
    private List<QuantDailyBar> bars = new ArrayList<QuantDailyBar>();
    private List<QuantFundamentalSnapshot> fundamentals = new ArrayList<QuantFundamentalSnapshot>();
    private double initialCapital = 1_000_000d;
    private double annualRiskFreeRate = 0.02d;
    public QuantStrategySpec getSpec() { return spec; }
    public void setSpec(QuantStrategySpec spec) { this.spec = spec; }
    public List<QuantDailyBar> getBars() { return bars; }
    public void setBars(List<QuantDailyBar> bars) { this.bars = bars; }
    public List<QuantFundamentalSnapshot> getFundamentals() { return fundamentals; }
    public void setFundamentals(List<QuantFundamentalSnapshot> fundamentals) { this.fundamentals = fundamentals; }
    public double getInitialCapital() { return initialCapital; }
    public void setInitialCapital(double initialCapital) { this.initialCapital = initialCapital; }
    public double getAnnualRiskFreeRate() { return annualRiskFreeRate; }
    public void setAnnualRiskFreeRate(double annualRiskFreeRate) { this.annualRiskFreeRate = annualRiskFreeRate; }
}
