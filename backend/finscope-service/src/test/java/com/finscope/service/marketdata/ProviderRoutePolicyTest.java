package com.finscope.service.marketdata;

import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.marketdata.MarketDataProvider;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderRoutePolicyTest {

    @Test
    void routePrefersHealthyFastProviderWithoutRoundRobin() {
        ProviderRequestGuard guard = new ProviderRequestGuard();
        MarketDataProvider tencent = provider("TENCENT_STOCK", "TENCENT", 10);
        MarketDataProvider sina = provider("SINA_STOCK", "SINA", 10);
        guard.recordSuccess("TENCENT_STOCK", "TENCENT",
                MarketDataCapability.REALTIME_STOCK_QUOTE, 80);
        guard.recordSuccess("SINA_STOCK", "SINA",
                MarketDataCapability.REALTIME_STOCK_QUOTE, 900);

        List<MarketDataProvider> ordered = new ProviderRoutePolicy(guard).order(
                Arrays.asList(sina, tencent), MarketDataCapability.REALTIME_STOCK_QUOTE);

        assertEquals("TENCENT_STOCK", ordered.get(0).providerCode());
    }

    private MarketDataProvider provider(String code, String family, int priority) {
        return new MarketDataProvider() {
            public String providerCode() { return code; }
            public String providerFamily() { return family; }
            public Set<MarketDataCapability> capabilities() {
                return Collections.singleton(MarketDataCapability.REALTIME_STOCK_QUOTE);
            }
            public int priority() { return priority; }
            public int batchLimit() { return 100; }
            public Duration minimumInterval() { return Duration.ZERO; }
            public Duration timeout() { return Duration.ofSeconds(1); }
        };
    }
}
