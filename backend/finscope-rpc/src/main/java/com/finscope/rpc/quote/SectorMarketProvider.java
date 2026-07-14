package com.finscope.rpc.quote;

import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketSnapshot;

/** 全市场板块目录 Provider，与已知代码的 QuoteAdapter 分离。 */
public interface SectorMarketProvider {
    String providerCode();
    boolean supports(SectorCategory category);
    SectorMarketSnapshot fetch(SectorCategory category);
}
