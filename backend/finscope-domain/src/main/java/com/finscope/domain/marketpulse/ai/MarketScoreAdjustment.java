package com.finscope.domain.marketpulse.ai;

import lombok.Data;

@Data
public class MarketScoreAdjustment {
    private com.finscope.common.enums.marketpulse.MarketStructureDimension dimension;
    private Integer delta;
    private String reason;
    private String counterEvidence;
    private java.util.List<String> factIds = new java.util.ArrayList<>();
}
