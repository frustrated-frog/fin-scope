package com.finscope.rpc.marketintel;

import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.marketdata.MarketDataProvider;
import com.finscope.rpc.marketdata.ProviderResult;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface CapitalFlowProvider extends MarketDataProvider {
    boolean supports(Instrument instrument);

    CapitalFlowData fetch(Instrument instrument, LocalDate asOfDate);

    default CapitalReplayData fetchReplay(Instrument instrument) {
        throw new ProviderContractException("UNSUPPORTED_CAPABILITY", "该数据源不支持资金复盘", false);
    }

    default ProviderResult<CapitalFlowData> fetchResult(Instrument instrument, LocalDate asOfDate) {
        CapitalFlowData data = fetch(instrument, asOfDate);
        return ProviderResult.of(data, LocalDateTime.now(), ProviderResult.hashOf(data.allPoints()), data.getWarnings());
    }
}
