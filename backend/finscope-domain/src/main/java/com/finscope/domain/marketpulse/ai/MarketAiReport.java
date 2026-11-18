package com.finscope.domain.marketpulse.ai;

import lombok.Data;

@Data
public class MarketAiReport {
    private String headline;
    private String summary;
    private String stateLabel;
    private com.finscope.domain.marketpulse.ai.MarketAiPoint change;
    private java.util.List<MarketAiPoint> drivers = new java.util.ArrayList<>();
    private java.util.List<MarketAiPoint> contradictions = new java.util.ArrayList<>();
    private java.util.List<MarketAiSector> sectors = new java.util.ArrayList<>();
    private java.util.List<MarketAiPoint> conditions = new java.util.ArrayList<>();
    private java.util.List<MarketScoreAdjustment> adjustments = new java.util.ArrayList<>();
    private java.util.List<MarketAiScenario> scenarios = new java.util.ArrayList<>();
}
