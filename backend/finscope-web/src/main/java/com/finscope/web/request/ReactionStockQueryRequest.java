package com.finscope.web.request;

import com.finscope.domain.investmentobservation.ReactionStockQuery;
import lombok.Data;

@Data
public class ReactionStockQueryRequest {
    private String query = "";
    private boolean trackingOnly;
    private int page = 1;
    private int size = 20;
    private long anchor = Long.MAX_VALUE;

    public ReactionStockQuery toQuery() {
        var result = new ReactionStockQuery();
        result.setQuery(query);
        result.setTrackingOnly(trackingOnly);
        result.setPage(page);
        result.setSize(size);
        result.setAnchor(anchor);
        return result;
    }
}
