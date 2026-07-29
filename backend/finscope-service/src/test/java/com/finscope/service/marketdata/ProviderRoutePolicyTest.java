package com.finscope.service.marketdata;

import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.marketdata.MarketDataProvider;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import com.finscope.rpc.provider.ExternalDataProvider;
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

    @Test
    void terminalFallbackAlwaysRemainsBehindRegularProviders() {
        ProviderRequestGuard guard = new ProviderRequestGuard();
        MarketDataProvider regular = provider("PYTHON_MARKET_DATA", "PYTHON", 5, false);
        MarketDataProvider terminal = provider("EASTMONEY_DIRECT", "EASTMONEY", 1, true);
        guard.recordSuccess("PYTHON_MARKET_DATA", "PYTHON",
                MarketDataCapability.REALTIME_STOCK_QUOTE, 100_000);
        guard.recordSuccess("EASTMONEY_DIRECT", "EASTMONEY",
                MarketDataCapability.REALTIME_STOCK_QUOTE, 1);

        List<MarketDataProvider> ordered = new ProviderRoutePolicy(guard).order(
                Arrays.asList(terminal, regular), MarketDataCapability.REALTIME_STOCK_QUOTE);

        assertEquals("PYTHON_MARKET_DATA", ordered.get(0).providerCode());
        assertEquals("EASTMONEY_DIRECT", ordered.get(1).providerCode());
    }

    @Test
    void ordersResearchProvidersWithTheSharedHealthPolicy() {
        ProviderRequestGuard guard = new ProviderRequestGuard();
        ExternalDataProvider primary = externalProvider("CNINFO", "CNINFO", 10);
        ExternalDataProvider fallback = externalProvider("EXCHANGE", "SZSE", 10);
        guard.recordSuccess("CNINFO", "CNINFO", "RESEARCH_ANNOUNCEMENT", 800);
        guard.recordSuccess("EXCHANGE", "SZSE", "RESEARCH_ANNOUNCEMENT", 40);

        List<ExternalDataProvider> ordered = new ProviderRoutePolicy(guard).orderExternal(
                Arrays.asList(primary, fallback), "RESEARCH_ANNOUNCEMENT", provider -> true);

        assertEquals("EXCHANGE", ordered.get(0).providerCode());
    }

    private MarketDataProvider provider(String code, String family, int priority) {
        return provider(code, family, priority, false);
    }

    private MarketDataProvider provider(String code, String family, int priority, boolean terminal) {
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
            public boolean isTerminalFallback() { return terminal; }
        };
    }

    private ExternalDataProvider externalProvider(String code, String family, int priority) {
        return new ExternalDataProvider() {
            public String providerCode() { return code; }
            public String providerFamily() { return family; }
            public int priority() { return priority; }
            public int batchLimit() { return 20; }
            public Duration minimumInterval() { return Duration.ZERO; }
            public Duration timeout() { return Duration.ofSeconds(2); }
        };
    }
}
