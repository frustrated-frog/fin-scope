package com.finscope.rpc.quote;

import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketSnapshot;
import com.finscope.rpc.marketdata.MarketDataProvider;
import com.finscope.rpc.marketdata.ProviderResult;

/** 全市场板块目录 Provider，与已知代码的 QuoteAdapter 分离。 */
public interface SectorMarketProvider extends MarketDataProvider {
    boolean supports(SectorCategory category);
    SectorMarketSnapshot fetch(SectorCategory category);

    default ProviderResult<SectorMarketSnapshot> fetchResult(SectorCategory category) {
        SectorMarketSnapshot data = fetch(category);
        return ProviderResult.of(data, data.getRetrievedAt(), data.getPayloadFingerprint(), data.getWarnings());
    }
}
