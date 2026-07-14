package com.finscope.web.response;

import com.finscope.service.instrument.SectorMarketOverview;

import java.util.List;
import java.util.stream.Collectors;

public final class SectorMarketOverviewResponse extends MarketDataQualityResponse {
    private String category;
    private List<SectorMarketEntryResponse> leaders;
    private List<SectorMarketEntryResponse> laggards;

    public static SectorMarketOverviewResponse of(SectorMarketOverview value) {
        SectorMarketOverviewResponse response = new SectorMarketOverviewResponse();
        response.category = value.getCategory().name();
        response.copyQuality(value.getQualityStatus(), value.getSourceCode(), value.getAsOf(),
                value.getRetrievedAt(), value.getStaleAgeSeconds(), value.getWarning(), value.getRefreshId());
        response.leaders = value.getLeaders().stream().map(SectorMarketEntryResponse::of).collect(Collectors.toList());
        response.laggards = value.getLaggards().stream().map(SectorMarketEntryResponse::of).collect(Collectors.toList());
        return response;
    }

    public String getCategory() { return category; }
    public List<SectorMarketEntryResponse> getLeaders() { return leaders; }
    public List<SectorMarketEntryResponse> getLaggards() { return laggards; }
}
