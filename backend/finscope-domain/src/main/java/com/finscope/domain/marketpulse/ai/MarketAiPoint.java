package com.finscope.domain.marketpulse.ai;

import lombok.Data;

@Data
public class MarketAiPoint {
    private String title;
    private String detail;
    private java.util.List<String> factIds = new java.util.ArrayList<>();
}
