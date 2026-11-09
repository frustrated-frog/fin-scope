package com.finscope.domain.investmentobservation;

import lombok.Data;

@Data
public class ReactionStockQuery {
    private String query = "";
    private boolean trackingOnly;
    private int page = 1;
    private int size = 20;
    private long anchor = Long.MAX_VALUE;
}
