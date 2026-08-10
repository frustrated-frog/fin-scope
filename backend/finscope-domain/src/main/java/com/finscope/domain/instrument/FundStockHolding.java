package com.finscope.domain.instrument;

/** 最近一期公开披露中的单只股票持仓。 */
public final class FundStockHolding {
    private final int rank;
    private final String stockCode;
    private final String stockName;
    private final double weightPct;
    private final Double sharesTenThousand;
    private final Double marketValueTenThousand;

    public FundStockHolding(int rank, String stockCode, String stockName, double weightPct,
                            Double sharesTenThousand, Double marketValueTenThousand) {
        this.rank = rank;
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.weightPct = weightPct;
        this.sharesTenThousand = sharesTenThousand;
        this.marketValueTenThousand = marketValueTenThousand;
    }

    public int getRank() { return rank; }
    public String getStockCode() { return stockCode; }
    public String getStockName() { return stockName; }
    public double getWeightPct() { return weightPct; }
    public Double getSharesTenThousand() { return sharesTenThousand; }
    public Double getMarketValueTenThousand() { return marketValueTenThousand; }
}
