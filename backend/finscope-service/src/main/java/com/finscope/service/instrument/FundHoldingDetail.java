package com.finscope.service.instrument;

import com.finscope.domain.marketdata.MarketDataQualityStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 基金最近披露持仓、批量行情与估算贡献的聚合结果。 */
public final class FundHoldingDetail {
    private final String fundCode;
    private final String fundName;
    private final LocalDate disclosureDate;
    private final LocalDateTime retrievedAt;
    private final LocalDateTime quoteAsOf;
    private final LocalDateTime quoteRetrievedAt;
    private final String quoteSource;
    private final MarketDataQualityStatus quoteQualityStatus;
    private final String quoteWarning;
    private final String refreshId;
    private final double topHoldingsWeightPct;
    private final Double estimatedContributionPct;
    private final int estimatedHoldingCount;
    private final int totalHoldingCount;
    private final boolean lookThrough;
    private final String note;
    private final List<FundHoldingPositionView> positions;

    public FundHoldingDetail(String fundCode, String fundName, LocalDate disclosureDate,
                             LocalDateTime retrievedAt, LocalDateTime quoteAsOf,
                             LocalDateTime quoteRetrievedAt, String quoteSource,
                             MarketDataQualityStatus quoteQualityStatus, String quoteWarning,
                             String refreshId, double topHoldingsWeightPct,
                             Double estimatedContributionPct, int estimatedHoldingCount,
                             int totalHoldingCount, boolean lookThrough, String note,
                             List<FundHoldingPositionView> positions) {
        this.fundCode = fundCode;
        this.fundName = fundName;
        this.disclosureDate = disclosureDate;
        this.retrievedAt = retrievedAt;
        this.quoteAsOf = quoteAsOf;
        this.quoteRetrievedAt = quoteRetrievedAt;
        this.quoteSource = quoteSource;
        this.quoteQualityStatus = quoteQualityStatus;
        this.quoteWarning = quoteWarning;
        this.refreshId = refreshId;
        this.topHoldingsWeightPct = topHoldingsWeightPct;
        this.estimatedContributionPct = estimatedContributionPct;
        this.estimatedHoldingCount = estimatedHoldingCount;
        this.totalHoldingCount = totalHoldingCount;
        this.lookThrough = lookThrough;
        this.note = note;
        this.positions = Collections.unmodifiableList(
                new ArrayList<FundHoldingPositionView>(positions));
    }

    public String getFundCode() { return fundCode; }
    public String getFundName() { return fundName; }
    public LocalDate getDisclosureDate() { return disclosureDate; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public LocalDateTime getQuoteAsOf() { return quoteAsOf; }
    public LocalDateTime getQuoteRetrievedAt() { return quoteRetrievedAt; }
    public String getQuoteSource() { return quoteSource; }
    public MarketDataQualityStatus getQuoteQualityStatus() { return quoteQualityStatus; }
    public String getQuoteWarning() { return quoteWarning; }
    public String getRefreshId() { return refreshId; }
    public double getTopHoldingsWeightPct() { return topHoldingsWeightPct; }
    public Double getEstimatedContributionPct() { return estimatedContributionPct; }
    public int getEstimatedHoldingCount() { return estimatedHoldingCount; }
    public int getTotalHoldingCount() { return totalHoldingCount; }
    public boolean isLookThrough() { return lookThrough; }
    public String getNote() { return note; }
    public List<FundHoldingPositionView> getPositions() { return positions; }
}
