package com.finscope.rpc.marketdata;

import com.finscope.domain.marketdata.MarketDataCapability;

import java.time.Duration;
import java.util.Set;

/** 外部市场数据 Provider 的可路由元数据合同。 */
public interface MarketDataProvider {

    /** 唯一且稳定的具体数据源代码，例如 SINA_STOCK。 */
    String providerCode();

    /** 共享限流或故障域的厂商家族，例如 SINA。 */
    String providerFamily();

    Set<MarketDataCapability> capabilities();

    /** 静态优先级，数值越小越优先；动态健康分由网关叠加。 */
    int priority();

    int batchLimit();

    Duration minimumInterval();

    Duration timeout();

    default boolean supports(MarketDataCapability capability) {
        return capability != null && capabilities().contains(capability);
    }
}
