package com.finscope.domain.investmentobservation;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ReactionEventPage {
    private List<ReactionSample> items;
    private Map<String, Long> counts;
    private Map<String, Long> pendingReasons;
    private Map<String, Integer> stockCounts;
    private Map<String, String> stockNames;
    private long total;
    private long anchor;
    private long revision;
    private int page;
    private int size;
}
