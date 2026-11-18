package com.finscope.web.response;

import com.finscope.common.enums.marketpulse.MarketAiStatus;
import com.finscope.domain.marketpulse.ai.*;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class MarketAiResponse {
    private MarketAiStatus status;
    private String businessDate;
    private String asOf;
    private String generatedAt;
    private String model;
    private String effort;
    private String message;
    private MarketAiReport report;
    private List<MarketAiFact> facts;
    private Map<String, String> sectorNames;
    private List<MarketStructureScore> structure;
    private int adjustmentLimit;
    private List<MarketScenarioReview> previousScenarios;
    private Long inputTokens;
    private Long outputTokens;

    public static MarketAiResponse of(MarketAiView view) {
        var response = new MarketAiResponse();
        response.setStatus(view.getStatus());
        response.setBusinessDate(view.getBusinessDate().toString());
        response.setAsOf(view.getAsOf() == null ? null : view.getAsOf().toString());
        response.setGeneratedAt(view.getGeneratedAt() == null ? null : view.getGeneratedAt().toString());
        response.setModel(view.getModel());
        response.setEffort(view.getEffort());
        response.setMessage(view.getMessage());
        response.setReport(view.getReport());
        response.setFacts(view.getFacts());
        response.setStructure(view.getStructure());
        response.setPreviousScenarios(view.getPreviousScenarios());
        response.setAdjustmentLimit(view.getAdjustmentLimit());
        response.setSectorNames(view.getSectorNames());
        response.setInputTokens(view.getInputTokens());
        response.setOutputTokens(view.getOutputTokens());
        return response;
    }
}
