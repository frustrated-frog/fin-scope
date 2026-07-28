package com.finscope.service.marketdata;

import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.marketdata.MarketDataProvider;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    private double score(MarketDataProvider provider, MarketDataCapability capability) {
        return provider.priority()
                + guard.failurePenalty(provider, capability)
                + (1.0d - guard.successRateEwma(provider, capability)) * 100.0d
                + guard.latencyEwmaMillis(provider, capability) / 100.0d;
    }
}
