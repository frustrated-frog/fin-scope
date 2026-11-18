package com.finscope.domain.marketpulse.ai;

import lombok.Data;

@Data
public class MarketStructureScore {
    private com.finscope.common.enums.marketpulse.MarketStructureDimension dimension;
    private String label;
    private Integer baselineScore;
    private Integer adjustment;
    private Integer finalScore;
    private String scope;
    private String reason;
    private String counterEvidence;
    private java.util.Map<String, Integer> components = new java.util.LinkedHashMap<>();
    private java.util.List<String> factIds = new java.util.ArrayList<>();
}
