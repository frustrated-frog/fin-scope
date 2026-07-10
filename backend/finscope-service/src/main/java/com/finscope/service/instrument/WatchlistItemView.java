package com.finscope.service.instrument;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.WatchlistItem;

/**
 * 自选面板展示视图：标的元信息 + 当前行情。
 */
public class WatchlistItemView {
    private final WatchlistItem item;
    private final Quote quote;

    public WatchlistItemView(WatchlistItem item, Quote quote) {
        this.item = item;
        this.quote = quote;
    }

    public WatchlistItem getItem() {
        return item;
    }

    public Quote getQuote() {
        return quote;
    }
}