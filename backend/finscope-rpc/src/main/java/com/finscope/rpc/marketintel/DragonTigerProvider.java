package com.finscope.rpc.marketintel;

import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.marketdata.MarketDataProvider;
import com.finscope.rpc.marketdata.ProviderResult;

import java.time.LocalDate;

public interface DragonTigerProvider extends MarketDataProvider {
    boolean supports(Instrument instrument);

    ProviderResult<DragonTigerData> fetch(
            Instrument instrument, LocalDate startDate, LocalDate endDate);
}
