package com.finscope.rpc.marketintel;

import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.acquisition.JdkAcquisitionRuntime;
import com.finscope.rpc.acquisition.RecordingAcquisitionRuntime;
import com.finscope.rpc.marketdata.MarketDataProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkFinanceHttpClientTest {
    @Test
    void sendsEachRequestedCallOnceAndUsesBrowserUserAgent() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<String> userAgent = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/quote", exchange -> {
            attempts.incrementAndGet();
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            RecordingAcquisitionRuntime runtime = new RecordingAcquisitionRuntime(new JdkAcquisitionRuntime());
            JdkFinanceHttpClient client = new JdkFinanceHttpClient(runtime, 1000, 1000, 1024);
            URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/quote");
            client.get("EASTMONEY", uri, Collections.emptyMap());
            client.get("EASTMONEY", uri, Collections.emptyMap());

            assertEquals(2, attempts.get());
            assertTrue(userAgent.get().startsWith("Mozilla/5.0"));
            assertEquals(2, runtime.getRequests().size());
            assertEquals(0, runtime.getRequests().get(0).getMaxRetries());
            assertEquals("MARKET_PROVIDER:EASTMONEY", runtime.getRequests().get(0).getPurpose());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void allowsALargerBoundedResponseOnlyWhenTheProviderRequestsIt() throws Exception {
        RecordingAcquisitionRuntime runtime = new RecordingAcquisitionRuntime(request ->
                new com.finscope.rpc.acquisition.AcquisitionResponse(
                        request.getUri(), request.getUri(), 200, Collections.emptyMap(),
                        "{}".getBytes(StandardCharsets.UTF_8), "{}", "application/json",
                        "UTF-8", "hash", 1, 1L, Instant.EPOCH));
        JdkFinanceHttpClient client = new JdkFinanceHttpClient(runtime, 1000, 1000, 1024);

        client.get("SEC_COMPANY_FACTS", URI.create("https://data.sec.gov/facts.json"),
                Collections.emptyMap(), 8 * 1024 * 1024);

        assertEquals(8 * 1024 * 1024, runtime.getRequests().get(0).getMaxResponseBytes());
    }

    @Test
    void postsFormDataThroughTheSharedAcquisitionRuntime() throws Exception {
        RecordingAcquisitionRuntime runtime = new RecordingAcquisitionRuntime(request ->
                new com.finscope.rpc.acquisition.AcquisitionResponse(
                        request.getUri(), request.getUri(), 200, Collections.emptyMap(),
                        "ok".getBytes(StandardCharsets.UTF_8), "ok", "text/html",
                        "UTF-8", "hash", 1, 1L, Instant.EPOCH));
        JdkFinanceHttpClient client = new JdkFinanceHttpClient(runtime, 1000, 1000, 1024);

        client.postForm("DART_XBRL", URI.create("https://englishdart.fss.or.kr/search"),
                "textCrpCik=00164779", Collections.singletonMap("X-Requested-With", "XMLHttpRequest"));

        assertEquals("POST", runtime.getRequests().get(0).getMethod());
        assertEquals("textCrpCik=00164779",
                new String(runtime.getRequests().get(0).getBodyBytes(), StandardCharsets.UTF_8));
        assertEquals("application/x-www-form-urlencoded",
                runtime.getRequests().get(0).getHeaders().get("Content-Type"));
    }

    @Test
    void doesNotRetryTransientHttpFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/unstable", exchange -> {
            int status = attempts.incrementAndGet() < 3 ? 503 : 200;
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            JdkFinanceHttpClient client = new JdkFinanceHttpClient(
                    new JdkAcquisitionRuntime(), 1000, 1000, 1024);
            URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/unstable");

            ProviderContractException error = assertThrows(ProviderContractException.class,
                    () -> client.get("EASTMONEY", uri, Collections.emptyMap()));

            assertEquals("HTTP_503", error.getErrorType());
            assertEquals(1, attempts.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void appliesProviderDeadlineToTheUnderlyingHttpRead() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/slow", exchange -> {
            attempts.incrementAndGet();
            try {
                Thread.sleep(250L);
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (Exception ignored) {
                // Client deadline may close the exchange before the delayed response is written.
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            JdkFinanceHttpClient client = new JdkFinanceHttpClient(
                    new JdkAcquisitionRuntime(), 1000, 1000, 1024);
            ProviderRequestGuard guard = new ProviderRequestGuard(
                    Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), millis -> { },
                    Duration.ZERO, 0, 3, Duration.ofSeconds(60));
            MarketDataProvider provider = new TestProvider(Duration.ofMillis(40));
            URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/slow");

            long started = System.nanoTime();
            ProviderContractException error = assertThrows(ProviderContractException.class,
                    () -> guard.execute(provider, MarketDataCapability.REALTIME_STOCK_QUOTE,
                            () -> client.get(provider.providerCode(), uri, Collections.emptyMap())));
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

            assertEquals("TIMEOUT", error.getErrorType());
            assertEquals(1, attempts.get());
            assertTrue(elapsedMillis < 180L, "socket read must inherit the provider deadline");
        } finally {
            server.stop(0);
        }
    }

    private static final class TestProvider implements MarketDataProvider {
        private final Duration timeout;

        private TestProvider(Duration timeout) { this.timeout = timeout; }
        public String providerCode() { return "LOCAL_HTTP"; }
        public String providerFamily() { return "LOCAL"; }
        public Set<MarketDataCapability> capabilities() {
            return Collections.singleton(MarketDataCapability.REALTIME_STOCK_QUOTE);
        }
        public int priority() { return 1; }
        public int batchLimit() { return 1; }
        public Duration minimumInterval() { return Duration.ZERO; }
        public Duration timeout() { return timeout; }
    }
}
