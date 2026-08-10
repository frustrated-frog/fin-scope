package com.finscope.web.response;

import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.service.instrument.FundHoldingDetail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** 基金最近披露持仓与实时估算贡献响应。 */
public final class FundHoldingDetailResponse {
    private String fundCode;
    private String fundName;
    private LocalDate disclosureDate;
    private LocalDateTime retrievedAt;
    private LocalDateTime quoteAsOf;
    private LocalDateTime quoteRetrievedAt;
    private String quoteSource;
    private String quoteQualityStatus;
    private String quoteWarning;
    private String refreshId;
    private double topHoldingsWeightPct;
    private Double estimatedContributionPct;
    private int estimatedHoldingCount;
    private int totalHoldingCount;
    private boolean lookThrough;
    private String note;
    private List<FundHoldingPositionResponse> holdings;

    public static FundHoldingDetailResponse of(FundHoldingDetail detail) {
        FundHoldingDetailResponse response = new FundHoldingDetailResponse();
        response.fundCode = detail.getFundCode();
        response.fundName = detail.getFundName();
        response.disclosureDate = detail.getDisclosureDate();
        response.retrievedAt = detail.getRetrievedAt();
        response.quoteAsOf = detail.getQuoteAsOf();
        response.quoteRetrievedAt = detail.getQuoteRetrievedAt();
        response.quoteSource = detail.getQuoteSource();
        MarketDataQualityStatus status = detail.getQuoteQualityStatus();
        response.quoteQualityStatus = status == null ? null : status.name();
        response.quoteWarning = detail.getQuoteWarning();
        response.refreshId = detail.getRefreshId();
        response.topHoldingsWeightPct = detail.getTopHoldingsWeightPct();
        response.estimatedContributionPct = detail.getEstimatedContributionPct();
        response.estimatedHoldingCount = detail.getEstimatedHoldingCount();
        response.totalHoldingCount = detail.getTotalHoldingCount();
        response.lookThrough = detail.isLookThrough();
        response.note = detail.getNote();
        response.holdings = detail.getPositions().stream()
                .map(FundHoldingPositionResponse::of)
                .collect(Collectors.toList());
        return response;
    }

    public String getFundCode() { return fundCode; }
    public String getFundName() { return fundName; }
    public LocalDate getDisclosureDate() { return disclosureDate; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public LocalDateTime getQuoteAsOf() { return quoteAsOf; }
    public LocalDateTime getQuoteRetrievedAt() { return quoteRetrievedAt; }
    public String getQuoteSource() { return quoteSource; }
    public String getQuoteQualityStatus() { return quoteQualityStatus; }
    public String getQuoteWarning() { return quoteWarning; }
    public String getRefreshId() { return refreshId; }
    public double getTopHoldingsWeightPct() { return topHoldingsWeightPct; }
    public Double getEstimatedContributionPct() { return estimatedContributionPct; }
    public int getEstimatedHoldingCount() { return estimatedHoldingCount; }
    public int getTotalHoldingCount() { return totalHoldingCount; }
    public boolean isLookThrough() { return lookThrough; }
    public String getNote() { return note; }
    public List<FundHoldingPositionResponse> getHoldings() { return holdings; }
}
