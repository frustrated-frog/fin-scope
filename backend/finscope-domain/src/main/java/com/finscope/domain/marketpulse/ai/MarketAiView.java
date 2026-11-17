package com.finscope.domain.marketpulse.ai;

import lombok.Data;

@Data
public class MarketAiView {
    private com.finscope.common.enums.marketpulse.MarketAiStatus status;
    private java.time.LocalDate businessDate;
    private java.time.LocalDateTime asOf;
    private java.time.LocalDateTime generatedAt;
    private String model;
    private String effort;
    private String message;
    private MarketAiReport report;
    private java.util.List<MarketAiFact> facts = new java.util.ArrayList<>();
    private java.util.Map<String, String> sectorNames = new java.util.LinkedHashMap<>();
    private Long inputTokens;
    private Long outputTokens;
}
