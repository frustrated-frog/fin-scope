package com.finscope.domain.quant.strategy;

import java.util.ArrayList;
import java.util.List;

public class QuantStrategySpec {
    private String name;
    private Long datasetId;
    private String benchmark;
    private String investmentHypothesis;
    private String riskBoundary;
    private List<FactorWeight> factors = new ArrayList<FactorWeight>();
    private Portfolio portfolio;
    private Filters filters;
    private Execution execution;
    private Cost cost;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getBenchmark() { return benchmark; }
    public void setBenchmark(String benchmark) { this.benchmark = benchmark; }
    public String getInvestmentHypothesis() { return investmentHypothesis; }
    public void setInvestmentHypothesis(String investmentHypothesis) { this.investmentHypothesis = investmentHypothesis; }
    public String getRiskBoundary() { return riskBoundary; }
    public void setRiskBoundary(String riskBoundary) { this.riskBoundary = riskBoundary; }
    public List<FactorWeight> getFactors() { return factors; }
    public void setFactors(List<FactorWeight> factors) { this.factors = factors; }
    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }
    public Filters getFilters() { return filters; }
    public void setFilters(Filters filters) { this.filters = filters; }
    public Execution getExecution() { return execution; }
    public void setExecution(Execution execution) { this.execution = execution; }
    public Cost getCost() { return cost; }
    public void setCost(Cost cost) { this.cost = cost; }

    public static class FactorWeight {
        private String code; private double weight; private String direction;
        public FactorWeight() { }
        public FactorWeight(String code, double weight, String direction) {
            this.code = code; this.weight = weight; this.direction = direction;
        }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
    }
    public static class Portfolio {
        private int topN; private int rebalanceEvery; private String weighting;
        public int getTopN() { return topN; }
        public void setTopN(int topN) { this.topN = topN; }
        public int getRebalanceEvery() { return rebalanceEvery; }
        public void setRebalanceEvery(int rebalanceEvery) { this.rebalanceEvery = rebalanceEvery; }
        public String getWeighting() { return weighting; }
        public void setWeighting(String weighting) { this.weighting = weighting; }
    }
    public static class Filters {
        private boolean excludeSt; private int minTradingDays; private double minAmount;
        public boolean isExcludeSt() { return excludeSt; }
        public void setExcludeSt(boolean excludeSt) { this.excludeSt = excludeSt; }
        public int getMinTradingDays() { return minTradingDays; }
        public void setMinTradingDays(int minTradingDays) { this.minTradingDays = minTradingDays; }
        public double getMinAmount() { return minAmount; }
        public void setMinAmount(double minAmount) { this.minAmount = minAmount; }
    }
    public static class Execution {
        private String signalPrice; private String fillPrice; private double slippageBps;
        public String getSignalPrice() { return signalPrice; }
        public void setSignalPrice(String signalPrice) { this.signalPrice = signalPrice; }
        public String getFillPrice() { return fillPrice; }
        public void setFillPrice(String fillPrice) { this.fillPrice = fillPrice; }
        public double getSlippageBps() { return slippageBps; }
        public void setSlippageBps(double slippageBps) { this.slippageBps = slippageBps; }
    }
    public static class Cost {
        private double buyCommission; private double sellCommission; private double stampDuty; private double minimumCommission;
        public double getBuyCommission() { return buyCommission; }
        public void setBuyCommission(double buyCommission) { this.buyCommission = buyCommission; }
        public double getSellCommission() { return sellCommission; }
        public void setSellCommission(double sellCommission) { this.sellCommission = sellCommission; }
        public double getStampDuty() { return stampDuty; }
        public void setStampDuty(double stampDuty) { this.stampDuty = stampDuty; }
        public double getMinimumCommission() { return minimumCommission; }
        public void setMinimumCommission(double minimumCommission) { this.minimumCommission = minimumCommission; }
    }
}
