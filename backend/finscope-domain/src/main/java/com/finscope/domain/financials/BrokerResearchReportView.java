package com.finscope.domain.financials;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BrokerResearchReportView {
    private BrokerResearchReport report;
    private BrokerResearchAnalysis analysis = new BrokerResearchAnalysis();
    private List<BrokerResearchForecast> forecasts = new ArrayList<BrokerResearchForecast>();
    private List<BrokerResearchClaim> claims = new ArrayList<BrokerResearchClaim>();
}
