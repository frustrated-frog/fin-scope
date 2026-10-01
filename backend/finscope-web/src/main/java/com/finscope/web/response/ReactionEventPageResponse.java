package com.finscope.web.response;

import com.finscope.domain.investmentobservation.ReactionEventPage;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ReactionEventPageResponse {
    private List<ReactionSampleResponse> items;
    private Map<String, Long> counts;
    private Map<String, Long> pendingReasons;
    private Map<String, Integer> stockCounts;
    private Map<String, String> stockNames;
    private long automaticEvents;
    private long linkedEvents;
    private String oldestPendingAt;
    private long total;
    private long anchor;
    private long revision;
    private int page;
    private int size;

    public static ReactionEventPageResponse from(ReactionEventPage page) {
        ReactionEventPageResponse result = new ReactionEventPageResponse();
        result.setItems(page.getItems().stream().map(ReactionSampleResponse::from).toList());
        result.setCounts(page.getCounts());
        result.setPendingReasons(page.getPendingReasons());
        result.setStockCounts(page.getStockCounts());
        result.setStockNames(page.getStockNames());
        result.setAutomaticEvents(page.getAutomaticEvents());
        result.setLinkedEvents(page.getLinkedEvents());
        result.setOldestPendingAt(page.getOldestPendingAt());
        result.setTotal(page.getTotal());
        result.setAnchor(page.getAnchor());
        result.setRevision(page.getRevision());
        result.setPage(page.getPage());
        result.setSize(page.getSize());
        return result;
    }
}
