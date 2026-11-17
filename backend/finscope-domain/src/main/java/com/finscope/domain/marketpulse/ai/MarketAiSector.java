package com.finscope.domain.marketpulse.ai;

import lombok.Data;

@Data
public class MarketAiSector {
    private String sectorCode;
    private String reason;
    private String watchCondition;
    private java.util.List<String> factIds = new java.util.ArrayList<>();
}
