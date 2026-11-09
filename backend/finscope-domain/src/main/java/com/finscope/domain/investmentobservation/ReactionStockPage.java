package com.finscope.domain.investmentobservation;

import lombok.Data;

import java.util.List;

@Data
public class ReactionStockPage {
    private List<ReactionStockSummary> items = List.of();
    private ReactionStockMatch matchedStock;
    private long total;
    private long anchor;
    private long revision;
    private int page;
    private int size;
}
