package com.finscope.domain.marketpulse.ai;

import lombok.Data;

@Data
public class MarketAiInput {
    private String fingerprint;
    private java.time.LocalDate businessDate;
    private java.time.LocalDateTime asOf;
    private java.util.List<MarketAiFact> facts = new java.util.ArrayList<>();
    private java.util.Map<String, String> sectorNames = new java.util.LinkedHashMap<>();
}
