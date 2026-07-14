package com.finscope.domain.quant.strategy;

import java.util.ArrayList;
import java.util.List;

public class QuantStrategySpec {
    /**
     * 名称。
     */
    private String name;
    /**
     * 数据集 ID。
     */
    private Long datasetId;
    /**
     * 基准标的。
     */
    private String benchmark;
    /**
     * 投资假设。
     */
    private String investmentHypothesis;
    /**
     * 风险边界。
     */
    private String riskBoundary;
    /**
     * 开始日期。
     */
    private java.time.LocalDate startDate;
    /**
     * 结束日期。
     */
    private java.time.LocalDate endDate;
    /**
     * 因子权重列表。
     */
    private List<FactorWeight> factors = new ArrayList<FactorWeight>();
    /**
     * 组合配置。
     */
    private Portfolio portfolio;
    /**
     * 过滤条件。
     */
    private Filters filters;
    /**
     * 执行设置。
     */
    private Execution execution;
    /**
     * 交易成本设置。
     */
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
    public java.time.LocalDate getStartDate() { return startDate; }
    public void setStartDate(java.time.LocalDate startDate) { this.startDate = startDate; }
    public java.time.LocalDate getEndDate() { return endDate; }
    public void setEndDate(java.time.LocalDate endDate) { this.endDate = endDate; }
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
        /**
         * 业务编码。
         */
        private String code;
        /**
         * 权重。
         */
        private double weight;
        /**
         * 方向。
         */
        private String direction;
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
        /**
         * 持仓数量上限。
         */
        private int topN;
        /**
         * 调仓周期。
         */
        private int rebalanceEvery;
        /**
         * 权重分配方式。
         */
        private String weighting;
        public int getTopN() { return topN; }
        public void setTopN(int topN) { this.topN = topN; }
        public int getRebalanceEvery() { return rebalanceEvery; }
        public void setRebalanceEvery(int rebalanceEvery) { this.rebalanceEvery = rebalanceEvery; }
        public String getWeighting() { return weighting; }
        public void setWeighting(String weighting) { this.weighting = weighting; }
    }
    public static class Filters {
        /**
         * 是否排除 ST 股票。
         */
        private boolean excludeSt;
        /**
         * 最少交易天数。
         */
        private int minTradingDays;
        /**
         * 最小成交额。
         */
        private double minAmount;
        public boolean isExcludeSt() { return excludeSt; }
        public void setExcludeSt(boolean excludeSt) { this.excludeSt = excludeSt; }
        public int getMinTradingDays() { return minTradingDays; }
        public void setMinTradingDays(int minTradingDays) { this.minTradingDays = minTradingDays; }
        public double getMinAmount() { return minAmount; }
        public void setMinAmount(double minAmount) { this.minAmount = minAmount; }
    }
    public static class Execution {
        /**
         * 信号价格口径。
         */
        private String signalPrice;
        /**
         * 成交价格口径。
         */
        private String fillPrice;
        /**
         * 滑点基点数。
         */
        private double slippageBps;
        public String getSignalPrice() { return signalPrice; }
        public void setSignalPrice(String signalPrice) { this.signalPrice = signalPrice; }
        public String getFillPrice() { return fillPrice; }
        public void setFillPrice(String fillPrice) { this.fillPrice = fillPrice; }
        public double getSlippageBps() { return slippageBps; }
        public void setSlippageBps(double slippageBps) { this.slippageBps = slippageBps; }
    }
    public static class Cost {
        /**
         * 买入佣金率。
         */
        private double buyCommission;
        /**
         * 卖出佣金率。
         */
        private double sellCommission;
        /**
         * 印花税率。
         */
        private double stampDuty;
        /**
         * 最低佣金。
         */
        private double minimumCommission;
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
