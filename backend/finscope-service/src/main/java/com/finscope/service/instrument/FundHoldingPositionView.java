package com.finscope.service.instrument;

import com.finscope.domain.marketdata.MarketDataQualityStatus;

import java.time.LocalDateTime;

/** 基金披露持仓与对应股票行情合并后的展示行。 */
public final class FundHoldingPositionView {
    private final int rank;
    private final String stockCode;
    private final String stockName;
    private final double weightPct;
    private final Double sharesTenThousand;
    private final Double marketValueTenThousand;
    private final Double latestPrice;
    private final Double changePct;
    private final Double estimatedContributionPct;
    private final boolean quoteValid;
    private final LocalDateTime quoteTime;
    private final MarketDataQualityStatus qualityStatus;
    private final String quoteNote;

    public FundHoldingPositionView(int rank, String stockCode, String stockName,
                                   double weightPct, Double sharesTenThousand,
                                   Double marketValueTenThousand, Double latestPrice,
                                   Double changePct, Double estimatedContributionPct,
                                   boolean quoteValid, LocalDateTime quoteTime,
                                   MarketDataQualityStatus qualityStatus, String quoteNote) {
        this.rank = rank;
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.weightPct = weightPct;
        this.sharesTenThousand = sharesTenThousand;
        this.marketValueTenThousand = marketValueTenThousand;
        this.latestPrice = latestPrice;
        this.changePct = changePct;
        this.estimatedContributionPct = estimatedContributionPct;
        this.quoteValid = quoteValid;
        this.quoteTime = quoteTime;
        this.qualityStatus = qualityStatus;
        this.quoteNote = quoteNote;
    }

    public int getRank() { return rank; }
    public String getStockCode() { return stockCode; }
    public String getStockName() { return stockName; }
    public double getWeightPct() { return weightPct; }
    public Double getSharesTenThousand() { return sharesTenThousand; }
    public Double getMarketValueTenThousand() { return marketValueTenThousand; }
    public Double getLatestPrice() { return latestPrice; }
    public Double getChangePct() { return changePct; }
    public Double getEstimatedContributionPct() { return estimatedContributionPct; }
    public boolean isQuoteValid() { return quoteValid; }
    public LocalDateTime getQuoteTime() { return quoteTime; }
    public MarketDataQualityStatus getQualityStatus() { return qualityStatus; }
    public String getQuoteNote() { return quoteNote; }
}
