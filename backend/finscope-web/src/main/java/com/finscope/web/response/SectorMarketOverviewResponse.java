package com.finscope.web.response;

import com.finscope.service.instrument.SectorMarketOverview;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public final class SectorMarketOverviewResponse {
    private String category;
    private String qualityStatus;
    private LocalDateTime retrievedAt;
    private String warning;
    private List<SectorMarketEntryResponse> leaders;
    private List<SectorMarketEntryResponse> laggards;

    public static SectorMarketOverviewResponse of(SectorMarketOverview value) {
        SectorMarketOverviewResponse response = new SectorMarketOverviewResponse();
        response.category = value.getCategory().name();
        response.qualityStatus = value.getQualityStatus().name();
        response.retrievedAt = value.getRetrievedAt();
        response.warning = value.getWarning();
        response.leaders = value.getLeaders().stream().map(SectorMarketEntryResponse::of).collect(Collectors.toList());
        response.laggards = value.getLaggards().stream().map(SectorMarketEntryResponse::of).collect(Collectors.toList());
        return response;
    }

    public String getCategory() { return category; }
    public String getQualityStatus() { return qualityStatus; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public String getWarning() { return warning; }
    public List<SectorMarketEntryResponse> getLeaders() { return leaders; }
    public List<SectorMarketEntryResponse> getLaggards() { return laggards; }
}
