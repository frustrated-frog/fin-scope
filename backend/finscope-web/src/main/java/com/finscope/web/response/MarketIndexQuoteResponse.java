package com.finscope.web.response;

import com.finscope.domain.instrument.Quote;
import com.finscope.service.instrument.MarketIndexView;

/** 市场指数卡片的只读响应契约。 */
public class MarketIndexQuoteResponse {
    private String code;
    private String name;
    private Double price;
    private Double changeAmount;
    private Double changePct;
    private boolean quoteValid;
    private String quoteNote;

    public static MarketIndexQuoteResponse of(MarketIndexView view) {
        MarketIndexQuoteResponse response = new MarketIndexQuoteResponse();
        response.code = view.getCode();
        response.name = view.getName();
        Quote quote = view.getQuote();
        if (quote == null) {
            response.quoteValid = false;
            response.quoteNote = "暂无行情";
            return response;
        }
        response.price = quote.getPrice();
        response.changeAmount = quote.getChangeAmount();
        response.changePct = quote.getChangePct();
        response.quoteValid = quote.isValid();
        response.quoteNote = quote.getNote();
        return response;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public Double getPrice() { return price; }
    public Double getChangeAmount() { return changeAmount; }
    public Double getChangePct() { return changePct; }
    public boolean isQuoteValid() { return quoteValid; }
    public String getQuoteNote() { return quoteNote; }
}
