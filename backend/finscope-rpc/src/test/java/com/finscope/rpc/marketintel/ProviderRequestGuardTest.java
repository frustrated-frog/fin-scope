package com.finscope.rpc.marketintel;

import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.quote.EastmoneySectorMarketProvider;
import com.finscope.rpc.quote.SinaStockQuoteAdapter;
import com.finscope.rpc.provider.ExternalDataProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRequestGuardTest {
    @Test
    void retriesOneRetryableFailure() {
        AtomicInteger calls = new AtomicInteger();
        ProviderRequestGuard guard = new ProviderRequestGuard(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                millis -> { }, Duration.ZERO, 1, 3, Duration.ofSeconds(60));
        String result = guard.execute("EASTMONEY", () -> {
            if (calls.getAndIncrement() == 0) throw new ProviderContractException("HTTP_503", "busy", true);
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(2, calls.get());
    }

    @Test
    void retriesOneConnectionFailure() {
        AtomicInteger calls = new AtomicInteger();
        ProviderRequestGuard guard = new ProviderRequestGuard(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                millis -> { }, Duration.ZERO, 1, 3, Duration.ofSeconds(60));

        String result = guard.execute("EASTMONEY", () -> {
            if (calls.getAndIncrement() == 0) throw new java.net.SocketException("unexpected end of file");
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, calls.get());
    }

    @Test
    void opensCircuitAfterThreeRetryableOperationsFail() {
        ProviderRequestGuard guard = new ProviderRequestGuard(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                millis -> { }, Duration.ZERO, 0, 3, Duration.ofSeconds(60));
        for (int i = 0; i < 3; i++) {
            assertThrows(ProviderContractException.class,
                    () -> guard.execute("EASTMONEY", () -> { throw new ProviderContractException("HTTP_503", "busy", true); }));
        }
        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> guard.execute("EASTMONEY", () -> "never"));
        assertEquals("CIRCUIT_OPEN", error.getErrorType());
    }

    @Test
    void familyCircuitIsSharedWithinCapabilityButIsolatedAcrossCapabilities() {
        ProviderRequestGuard guard = new ProviderRequestGuard(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                millis -> { }, Duration.ZERO, 0, 3, Duration.ofSeconds(60));
        SinaStockQuoteAdapter sina = new SinaStockQuoteAdapter();
        EastmoneySectorMarketProvider sector = new EastmoneySectorMarketProvider(null);
        TestProvider sectorBackup = new TestProvider("EASTMONEY_SECTOR_BACKUP", "EASTMONEY",
                MarketDataCapability.SECTOR_CATALOG);
        TestProvider flow = new TestProvider("EASTMONEY_CAPITAL_FLOW", "EASTMONEY",
                MarketDataCapability.CAPITAL_FLOW_5M);

        for (int i = 0; i < 3; i++) {
            assertThrows(ProviderContractException.class, () -> guard.execute(sector,
                    MarketDataCapability.SECTOR_CATALOG,
                    () -> { throw new ProviderContractException("HTTP_503", "busy", true); }));
        }
        assertFalse(guard.isAvailable(sector, MarketDataCapability.SECTOR_CATALOG));
        assertFalse(guard.isAvailable(sectorBackup, MarketDataCapability.SECTOR_CATALOG));
        assertTrue(guard.isAvailable(sina, MarketDataCapability.REALTIME_STOCK_QUOTE));
        assertTrue(guard.isAvailable(flow, MarketDataCapability.CAPITAL_FLOW_5M));
        assertFalse(guard.isFamilyAvailable(MarketDataCapability.SECTOR_CATALOG, "EASTMONEY"));
        assertTrue(guard.isFamilyAvailable(MarketDataCapability.CAPITAL_FLOW_5M, "EASTMONEY"));

        assertEquals("ok", guard.execute(MarketDataCapability.CAPITAL_FLOW_5M,
                "EASTMONEY", () -> "ok"));
    }

    @Test
    void eastmoneyRequestsShareOneSecondThrottleAcrossCapabilities() {
        List<Long> sleeps = new ArrayList<Long>();
        ProviderRequestGuard guard = new ProviderRequestGuard(
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), sleeps::add,
                Duration.ZERO, 0, 3, Duration.ofSeconds(60));
        TestProvider sector = new TestProvider("EASTMONEY_SECTOR", "EASTMONEY",
                MarketDataCapability.SECTOR_CATALOG);
        TestProvider flow = new TestProvider("EASTMONEY_FLOW", "EASTMONEY",
                MarketDataCapability.CAPITAL_FLOW_5M);

        guard.execute(sector, MarketDataCapability.SECTOR_CATALOG, () -> "sector");
        guard.execute(flow, MarketDataCapability.CAPITAL_FLOW_5M, () -> "flow");

        assertEquals(Arrays.asList(1000L), sleeps);
    }

    @Test
    void nonMarketProvidersShareTheSameReliabilityRuntime() {
        AtomicInteger calls = new AtomicInteger();
        ProviderRequestGuard guard = new ProviderRequestGuard(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                millis -> { }, Duration.ZERO, 1, 3, Duration.ofSeconds(60));
        ExternalDataProvider provider = externalProvider("CNINFO_ANNOUNCEMENT", "CNINFO");

        String result = guard.execute(provider, "RESEARCH_ANNOUNCEMENT", () -> {
            if (calls.getAndIncrement() == 0) {
                throw new ProviderContractException("HTTP_503", "busy", true);
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, calls.get());
        assertTrue(guard.isAvailable(provider, "RESEARCH_ANNOUNCEMENT"));
    }

    @Test
    void providerDeadlineIsNestedAndAlwaysCleanedUp() {
        ProviderRequestGuard guard = new ProviderRequestGuard(Clock.systemUTC(), millis -> { },
                Duration.ZERO, 0, 3, Duration.ofSeconds(60));
        TestProvider provider = new TestProvider("FLOW", "LOCAL",
                MarketDataCapability.CAPITAL_FLOW_5M);

        assertEquals(Long.MAX_VALUE, ProviderCallDeadline.remainingMillis());
        try (ProviderCallDeadline.Scope ignored = ProviderCallDeadline.open(Duration.ofSeconds(2))) {
            guard.execute(provider, MarketDataCapability.CAPITAL_FLOW_5M, () -> {
                assertTrue(ProviderCallDeadline.remainingMillis() <= 1_000L);
                return "ok";
            });
            assertTrue(ProviderCallDeadline.remainingMillis() <= 2_000L);
        }
        assertEquals(Long.MAX_VALUE, ProviderCallDeadline.remainingMillis());
    }

    @Test
    void providerDeadlineCanBePropagatedToAWorkerThread() {
        Supplier<Long> operation;
        try (ProviderCallDeadline.Scope ignored = ProviderCallDeadline.open(Duration.ofSeconds(1))) {
            operation = ProviderCallDeadline.propagate(ProviderCallDeadline::remainingMillis);
        }

        assertEquals(Long.MAX_VALUE, ProviderCallDeadline.remainingMillis());
        long workerRemainingMillis = CompletableFuture.supplyAsync(operation).join();

        assertTrue(workerRemainingMillis > 0L);
        assertTrue(workerRemainingMillis <= 1_000L);
        assertEquals(Long.MAX_VALUE, ProviderCallDeadline.remainingMillis());
    }

    private static final class TestProvider implements com.finscope.rpc.marketdata.MarketDataProvider {
        private final String code;
        private final String family;
        private final MarketDataCapability capability;

        private TestProvider(String code, String family, MarketDataCapability capability) {
            this.code = code;
            this.family = family;
            this.capability = capability;
        }

        public String providerCode() { return code; }
        public String providerFamily() { return family; }
        public java.util.Set<MarketDataCapability> capabilities() {
            return java.util.Collections.singleton(capability);
        }
        public int priority() { return 10; }
        public int batchLimit() { return 1; }
        public Duration minimumInterval() { return Duration.ZERO; }
        public Duration timeout() { return Duration.ofSeconds(1); }
    }

    private ExternalDataProvider externalProvider(String code, String family) {
        return new ExternalDataProvider() {
            public String providerCode() { return code; }
            public String providerFamily() { return family; }
            public int priority() { return 10; }
            public int batchLimit() { return 20; }
            public Duration minimumInterval() { return Duration.ZERO; }
            public Duration timeout() { return Duration.ofSeconds(2); }
        };
    }
}
