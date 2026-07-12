package com.finscope.service.instrument;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.WatchlistItem;

/**
 * 自选面板展示视图：标的元信息 + 当前行情。
 */
public class WatchlistItemView {
    private final WatchlistItem item;
    private final Quote quote;
    private final String attributionSummary;

    public WatchlistItemView(WatchlistItem item, Quote quote, String attributionSummary) {
        this.item = item;
        this.quote = quote;
        this.attributionSummary = attributionSummary;
    }

    public WatchlistItem getItem() {
        return item;
    }

    public Quote getQuote() {
        return quote;
    }

    public String getAttributionSummary() {
        return attributionSummary;
    }
}
