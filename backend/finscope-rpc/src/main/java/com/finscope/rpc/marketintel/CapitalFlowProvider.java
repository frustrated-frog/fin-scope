package com.finscope.rpc.marketintel;

import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.marketdata.MarketDataProvider;
import com.finscope.rpc.marketdata.ProviderResult;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface CapitalFlowProvider extends MarketDataProvider {
    boolean supports(Instrument instrument);
    CapitalFlowData fetch(Instrument instrument, LocalDate asOfDate);

    default ProviderResult<CapitalFlowData> fetchResult(Instrument instrument, LocalDate asOfDate) {
        CapitalFlowData data = fetch(instrument, asOfDate);
        return ProviderResult.of(data, LocalDateTime.now(), ProviderResult.hashOf(data.allPoints()), data.getWarnings());
    }
}
