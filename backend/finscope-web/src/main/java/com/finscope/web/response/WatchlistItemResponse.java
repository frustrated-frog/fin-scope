package com.finscope.web.response;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.service.instrument.WatchlistItemView;
import com.finscope.dao.attribution.AttributionRepository;

/**
 * 自选面板响应：标的元信息 + 行情扁平化，方便前端直接渲染。
 */
public class WatchlistItemResponse {
    private Long id;
    private String code;
    private String type;
    private String name;
    private String market;
    private String groupName;

    private Double price;
    private Double confirmedNav;
    private String confirmedNavDate;
    private Double confirmedNavChangePct;
    private Double changePct;
    private Double changeAmount;
    private Double turnover;
    private Double open;
    private Double high;
    private Double low;
    private Double amplitude;
    private boolean quoteValid;
    private String quoteNote;
    private String quoteDate;
    private String attributionSummary;
    private Long attributionReportId;
    private String attributionReportDate;
    private Double attributionChangePct;

    public static WatchlistItemResponse of(WatchlistItemView view) {
        WatchlistItem item = view.getItem();
        Quote quote = view.getQuote();
        WatchlistItemResponse response = new WatchlistItemResponse();
        response.id = item.getId();
        response.code = item.getCode();
        response.type = item.getType();
        response.name = item.getName();
        response.market = item.getMarket();
        response.groupName = item.getGroupName();
        response.attributionSummary = view.getAttributionSummary();
        AttributionRepository.AttributionSummaryView attribution = view.getAttribution();
        if (attribution != null) {
            response.attributionReportId = attribution.getReportId();
            response.attributionReportDate = attribution.getReportDate() == null ? null : attribution.getReportDate().toString();
            response.attributionChangePct = attribution.getChangePct();
        }
        if (quote != null) {
            response.price = quote.getPrice();
            response.confirmedNav = quote.getConfirmedNav();
            response.confirmedNavDate = quote.getConfirmedNavDate();
            response.confirmedNavChangePct = quote.getConfirmedNavChangePct();
            response.changePct = quote.getChangePct();
            response.changeAmount = quote.getChangeAmount();
            response.turnover = quote.getTurnover();
            response.open = quote.getOpen();
            response.high = quote.getHigh();
            response.low = quote.getLow();
            response.amplitude = quote.getAmplitude();
            response.quoteValid = quote.isValid();
            response.quoteNote = quote.getNote();
            response.quoteDate = "FUND".equals(item.getType()) && quote.getConfirmedNavDate() != null
                    ? quote.getConfirmedNavDate()
                    : quote.getQuoteTime() == null ? null : quote.getQuoteTime().toLocalDate().toString();
        } else {
            response.quoteValid = false;
            response.quoteNote = "暂无行情";
        }
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getMarket() {
        return market;
    }

    public String getGroupName() {
        return groupName;
    }

    public Double getPrice() {
        return price;
    }

    public Double getConfirmedNav() { return confirmedNav; }
    public String getConfirmedNavDate() { return confirmedNavDate; }
    public Double getConfirmedNavChangePct() { return confirmedNavChangePct; }

    public Double getChangePct() {
        return changePct;
    }

    public Double getChangeAmount() {
        return changeAmount;
    }

    public Double getTurnover() {
        return turnover;
    }

    public Double getOpen() {
        return open;
    }

    public Double getHigh() {
        return high;
    }

    public Double getLow() {
        return low;
    }

    public Double getAmplitude() {
        return amplitude;
    }

    public boolean isQuoteValid() {
        return quoteValid;
    }

    public String getQuoteNote() {
        return quoteNote;
    }

    public String getAttributionSummary() {
        return attributionSummary;
    }

    public String getQuoteDate() { return quoteDate; }
    public Long getAttributionReportId() { return attributionReportId; }
    public String getAttributionReportDate() { return attributionReportDate; }
    public Double getAttributionChangePct() { return attributionChangePct; }
}
