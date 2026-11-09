package com.finscope.web.response;

import com.finscope.domain.investmentobservation.ReactionStockMatch;
import com.finscope.domain.investmentobservation.ReactionStockPage;
import com.finscope.domain.investmentobservation.ReactionStockSummary;

import java.util.List;

public record ReactionStockPageResponse(List<ReactionStockSummary> items, ReactionStockMatch matchedStock,
                                        long total, long anchor, long revision, int page, int size) {
    public static ReactionStockPageResponse from(ReactionStockPage page) {
        return new ReactionStockPageResponse(page.getItems(), page.getMatchedStock(), page.getTotal(),
                page.getAnchor(), page.getRevision(), page.getPage(), page.getSize());
    }
}
