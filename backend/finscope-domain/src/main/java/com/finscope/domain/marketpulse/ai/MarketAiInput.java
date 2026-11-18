package com.finscope.domain.marketpulse.ai;

import lombok.Data;

@Data
public class MarketAiInput {
    private String fingerprint;
    private java.time.LocalDate businessDate;
    private java.time.LocalDateTime asOf;
    private java.util.List<MarketAiFact> facts = new java.util.ArrayList<>();
    private java.util.Map<String, String> sectorNames = new java.util.LinkedHashMap<>();
    private java.util.List<MarketStructureScore> structure = new java.util.ArrayList<>();
    private int adjustmentLimit = 15;
    private java.util.Map<com.finscope.common.enums.marketpulse.MarketScenarioMetric, Double> observations = new java.util.LinkedHashMap<>();
    private String previousBusinessDate;
    private java.util.Map<com.finscope.common.enums.marketpulse.MarketScenarioMetric, Integer> observationSamples = new java.util.LinkedHashMap<>();
}
