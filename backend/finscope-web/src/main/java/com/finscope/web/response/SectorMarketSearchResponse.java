package com.finscope.web.response;

import com.finscope.service.instrument.SectorMarketSearchResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public final class SectorMarketSearchResponse {
    private String qualityStatus;
    private LocalDateTime retrievedAt;
    private String warning;
    private List<SectorMarketEntryResponse> items;

    public static SectorMarketSearchResponse of(SectorMarketSearchResult value) {
        SectorMarketSearchResponse response = new SectorMarketSearchResponse();
        response.qualityStatus = value.getQualityStatus().name();
        response.retrievedAt = value.getRetrievedAt();
        response.warning = value.getWarning();
        response.items = value.getItems().stream().map(SectorMarketEntryResponse::of).collect(Collectors.toList());
        return response;
    }

    public String getQualityStatus() { return qualityStatus; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public String getWarning() { return warning; }
    public List<SectorMarketEntryResponse> getItems() { return items; }
}
