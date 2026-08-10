package com.finscope.web.response;

import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.service.instrument.FundHoldingPositionView;

import java.time.LocalDateTime;

/** 基金持仓详情中的单只股票响应。 */
public final class FundHoldingPositionResponse {
    private int rank;
    private String stockCode;
    private String stockName;
    private double weightPct;
    private Double sharesTenThousand;
    private Double marketValueTenThousand;
    private Double latestPrice;
    private Double changePct;
    private Double estimatedContributionPct;
    private boolean quoteValid;
    private LocalDateTime quoteTime;
    private String qualityStatus;
    private String quoteNote;

    public static FundHoldingPositionResponse of(FundHoldingPositionView view) {
        FundHoldingPositionResponse response = new FundHoldingPositionResponse();
        response.rank = view.getRank();
        response.stockCode = view.getStockCode();
        response.stockName = view.getStockName();
        response.weightPct = view.getWeightPct();
        response.sharesTenThousand = view.getSharesTenThousand();
        response.marketValueTenThousand = view.getMarketValueTenThousand();
        response.latestPrice = view.getLatestPrice();
        response.changePct = view.getChangePct();
        response.estimatedContributionPct = view.getEstimatedContributionPct();
        response.quoteValid = view.isQuoteValid();
        response.quoteTime = view.getQuoteTime();
        MarketDataQualityStatus status = view.getQualityStatus();
        response.qualityStatus = status == null ? null : status.name();
        response.quoteNote = view.getQuoteNote();
        return response;
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
    public String getQualityStatus() { return qualityStatus; }
    public String getQuoteNote() { return quoteNote; }
}
