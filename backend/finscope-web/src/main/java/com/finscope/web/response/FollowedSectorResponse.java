package com.finscope.web.response;

import com.finscope.dao.attribution.AttributionRepository;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.service.instrument.WatchlistItemView;

/** 用户关注板块及其单标的行情、归因摘要。 */
public final class FollowedSectorResponse {
    private Long id;
    private String code;
    private String name;
    private Double price;
    private Double changePct;
    private Double changeAmount;
    private Double turnover;
    private boolean quoteValid;
    private String quoteNote;
    private String quoteDate;
    private String attributionSummary;
    private Long attributionReportId;
    private String attributionReportDate;
    private Double attributionChangePct;

    public static FollowedSectorResponse of(WatchlistItemView view) {
        WatchlistItem item = view.getItem();
        Quote quote = view.getQuote();
        FollowedSectorResponse response = new FollowedSectorResponse();
        response.id = item.getId();
        response.code = item.getCode();
        response.name = item.getName();
        response.attributionSummary = view.getAttributionSummary();
        AttributionRepository.AttributionSummaryView attribution = view.getAttribution();
        if (attribution != null) {
            response.attributionReportId = attribution.getReportId();
            response.attributionReportDate = attribution.getReportDate() == null
                    ? null : attribution.getReportDate().toString();
            response.attributionChangePct = attribution.getChangePct();
        }
        if (quote == null) {
            response.quoteValid = false;
            response.quoteNote = "暂无行情";
            return response;
        }
        response.price = quote.getPrice();
        response.changePct = quote.getChangePct();
        response.changeAmount = quote.getChangeAmount();
        response.turnover = quote.getTurnover();
        response.quoteValid = quote.isValid();
        response.quoteNote = quote.getNote();
        response.quoteDate = quote.getQuoteTime() == null ? null : quote.getQuoteTime().toLocalDate().toString();
        return response;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public Double getPrice() { return price; }
    public Double getChangePct() { return changePct; }
    public Double getChangeAmount() { return changeAmount; }
    public Double getTurnover() { return turnover; }
    public boolean isQuoteValid() { return quoteValid; }
    public String getQuoteNote() { return quoteNote; }
    public String getQuoteDate() { return quoteDate; }
    public String getAttributionSummary() { return attributionSummary; }
    public Long getAttributionReportId() { return attributionReportId; }
    public String getAttributionReportDate() { return attributionReportDate; }
    public Double getAttributionChangePct() { return attributionChangePct; }
}
