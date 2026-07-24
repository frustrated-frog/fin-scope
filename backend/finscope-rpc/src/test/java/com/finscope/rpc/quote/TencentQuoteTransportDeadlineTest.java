package com.finscope.rpc.quote;

import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.marketdata.MarketDataProvider;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TencentQuoteTransportDeadlineTest {
    @Test
    void slowChunksCannotExtendTheOverallProviderDeadline() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/quote", exchange -> {
            attempts.incrementAndGet();
            try {
                exchange.sendResponseHeaders(200, 0);
                for (int index = 0; index < 6; index++) {
                    exchange.getResponseBody().write(("chunk-" + index).getBytes("GBK"));
                    exchange.getResponseBody().flush();
                    Thread.sleep(40L);
                }
            } catch (Exception ignored) {
                // The client is expected to close the connection at its overall deadline.
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            ProviderRequestGuard guard = new ProviderRequestGuard(
                    Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), millis -> { },
                    Duration.ZERO, 0, 3, Duration.ofSeconds(60));
            MarketDataProvider provider = new TestProvider(Duration.ofMillis(100));
            String url = "http://localhost:" + server.getAddress().getPort() + "/quote";

            long started = System.nanoTime();
            ProviderContractException error = assertThrows(ProviderContractException.class,
                    () -> guard.execute(provider, MarketDataCapability.REALTIME_STOCK_QUOTE,
                            () -> TencentQuoteParser.requestGbk(url)));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertEquals("TIMEOUT", error.getErrorType());
            assertEquals(1, attempts.get());
            assertTrue(elapsedMillis < 220L, "slow chunks must not reset the overall deadline");
        } finally {
            server.stop(0);
        }
    }

    private static final class TestProvider implements MarketDataProvider {
        private final Duration timeout;
        private TestProvider(Duration timeout) { this.timeout = timeout; }
        public String providerCode() { return "TENCENT_TEST"; }
        public String providerFamily() { return "TENCENT"; }
        public Set<MarketDataCapability> capabilities() {
            return Collections.singleton(MarketDataCapability.REALTIME_STOCK_QUOTE);
        }
        public int priority() { return 1; }
        public int batchLimit() { return 1; }
        public Duration minimumInterval() { return Duration.ZERO; }
        public Duration timeout() { return timeout; }
    }
}
