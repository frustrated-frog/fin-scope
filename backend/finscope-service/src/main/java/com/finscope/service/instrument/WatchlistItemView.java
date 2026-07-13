package com.finscope.service.instrument;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.dao.attribution.AttributionRepository;

/**
 * 自选面板展示视图：标的元信息 + 当前行情。
 */
public class WatchlistItemView {
    private final WatchlistItem item;
    private final Quote quote;
    private final AttributionRepository.AttributionSummaryView attribution;

    public WatchlistItemView(WatchlistItem item, Quote quote, AttributionRepository.AttributionSummaryView attribution) {
        this.item = item;
        this.quote = quote;
        this.attribution = attribution;
    }

    public WatchlistItem getItem() {
        return item;
    }

    public Quote getQuote() {
        return quote;
    }

    public String getAttributionSummary() {
        return attribution == null ? null : attribution.getSummary();
    }

    public AttributionRepository.AttributionSummaryView getAttribution() { return attribution; }
}
