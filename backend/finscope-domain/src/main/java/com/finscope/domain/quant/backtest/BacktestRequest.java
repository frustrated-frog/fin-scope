package com.finscope.domain.quant.backtest;

import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import com.finscope.domain.quant.data.QuantUniverseMember;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import java.util.ArrayList;
import java.util.List;

public class BacktestRequest {
    /**
     * 策略规格。
     */
    private QuantStrategySpec spec;
    /**
     * 日线行情列表。
     */
    private List<QuantDailyBar> bars = new ArrayList<QuantDailyBar>();
    /**
     * 基本面快照列表。
     */
    private List<QuantFundamentalSnapshot> fundamentals = new ArrayList<QuantFundamentalSnapshot>();
    /**
     * 股票池成员列表。
     */
    private List<QuantUniverseMember> universe = new ArrayList<QuantUniverseMember>();
    /**
     * 初始资金。
     */
    private double initialCapital = 1_000_000d;
    /**
     * 年化无风险利率。
     */
    private double annualRiskFreeRate = 0.02d;
    public QuantStrategySpec getSpec() { return spec; }
    public void setSpec(QuantStrategySpec spec) { this.spec = spec; }
    public List<QuantDailyBar> getBars() { return bars; }
    public void setBars(List<QuantDailyBar> bars) { this.bars = bars; }
    public List<QuantFundamentalSnapshot> getFundamentals() { return fundamentals; }
    public void setFundamentals(List<QuantFundamentalSnapshot> fundamentals) { this.fundamentals = fundamentals; }
    public List<QuantUniverseMember> getUniverse() { return universe; }
    public void setUniverse(List<QuantUniverseMember> universe) { this.universe = universe; }
    public double getInitialCapital() { return initialCapital; }
    public void setInitialCapital(double initialCapital) { this.initialCapital = initialCapital; }
    public double getAnnualRiskFreeRate() { return annualRiskFreeRate; }
    public void setAnnualRiskFreeRate(double annualRiskFreeRate) { this.annualRiskFreeRate = annualRiskFreeRate; }
}
