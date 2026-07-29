package com.finscope.service.marketdata;

import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.marketdata.MarketDataProvider;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import com.finscope.rpc.provider.ExternalDataProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** 按静态优先级、成功率和延迟动态排序健康 Provider。 */
@Component
public class ProviderRoutePolicy {
    private final ProviderRequestGuard guard;

    public ProviderRoutePolicy(ProviderRequestGuard guard) {
        this.guard = guard;
    }

    public <P extends MarketDataProvider> List<P> order(List<P> providers,
                                                        MarketDataCapability capability) {
        List<P> available = new ArrayList<P>();
        for (P provider : providers) {
            if (provider.supports(capability) && guard.isAvailable(provider, capability)) {
                available.add(provider);
            }
        }
        available.sort(Comparator
                .comparing((P provider) -> provider.isTerminalFallback())
                .thenComparingDouble(provider -> score(provider, capability)));
        return available;
    }

    public <P extends ExternalDataProvider> List<P> orderExternal(List<P> providers,
                                                                  String capabilityCode,
                                                                  Predicate<P> supports) {
        List<P> available = new ArrayList<P>();
        for (P provider : providers) {
            if (supports.test(provider) && guard.isAvailable(provider, capabilityCode)) {
                available.add(provider);
            }
        }
        available.sort(Comparator
                .comparing((P provider) -> provider.isTerminalFallback())
                .thenComparingDouble(provider -> scoreExternal(provider, capabilityCode)));
        return available;
    }

    private double score(MarketDataProvider provider, MarketDataCapability capability) {
        return provider.priority()
                + guard.failurePenalty(provider, capability)
                + (1.0d - guard.successRateEwma(provider, capability)) * 100.0d
                + guard.latencyEwmaMillis(provider, capability) / 100.0d;
    }

    private double scoreExternal(ExternalDataProvider provider, String capabilityCode) {
        return provider.priority()
                + guard.failurePenalty(provider, capabilityCode)
                + (1.0d - guard.successRateEwma(provider, capabilityCode)) * 100.0d
                + guard.latencyEwmaMillis(provider, capabilityCode) / 100.0d;
    }
}
