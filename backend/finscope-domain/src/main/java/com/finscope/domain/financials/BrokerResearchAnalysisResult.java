package com.finscope.domain.financials;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BrokerResearchAnalysisResult {
    private String analysisMode;
    private String qualityLevel;
    private String errorMessage;
    private BrokerResearchAnalysis analysis = new BrokerResearchAnalysis();
    private List<BrokerResearchForecast> forecasts = new ArrayList<BrokerResearchForecast>();
    private List<BrokerResearchClaim> claims = new ArrayList<BrokerResearchClaim>();
}
