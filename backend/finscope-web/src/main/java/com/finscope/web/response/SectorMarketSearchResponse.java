package com.finscope.web.response;

import com.finscope.service.instrument.SectorMarketSearchResult;

import java.util.List;
import java.util.stream.Collectors;

public final class SectorMarketSearchResponse extends MarketDataQualityResponse {
    private List<SectorMarketEntryResponse> items;

    public static SectorMarketSearchResponse of(SectorMarketSearchResult value) {
        SectorMarketSearchResponse response = new SectorMarketSearchResponse();
        response.copyQuality(value.getQualityStatus(), value.getSourceCode(), value.getAsOf(),
                value.getRetrievedAt(), value.getStaleAgeSeconds(), value.getWarning(), value.getRefreshId());
        response.items = value.getItems().stream().map(SectorMarketEntryResponse::of).collect(Collectors.toList());
        return response;
    }

    public List<SectorMarketEntryResponse> getItems() { return items; }
}
