package com.finscope.rpc.marketdata;

import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.provider.ExternalDataProvider;

import java.util.Set;

/** 外部市场数据 Provider 的可路由元数据合同。 */
public interface MarketDataProvider extends ExternalDataProvider {

    Set<MarketDataCapability> capabilities();

    default boolean supports(MarketDataCapability capability) {
        return capability != null && capabilities().contains(capability);
    }
}
