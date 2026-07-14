package com.finscope.rpc.marketintel;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkFinanceHttpClientTest {
    @Test
    void throttlesEveryRequestFromTheSameProviderAndUsesBrowserUserAgent() throws Exception {
        AtomicReference<String> userAgent = new AtomicReference<String>();
        AtomicReference<String> connectionHeader = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/quote", exchange -> {
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            connectionHeader.set(exchange.getRequestHeaders().getFirst("Connection"));
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            JdkFinanceHttpClient client = new JdkFinanceHttpClient(1000, 1000, 1024);
            URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/quote");
            long started = System.nanoTime();
            client.get("EASTMONEY", uri, Collections.emptyMap());
            client.get("EASTMONEY", uri, Collections.emptyMap());
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

            assertTrue(elapsedMillis >= 900, "two calls should be spaced by about one second");
            assertTrue(userAgent.get().startsWith("Mozilla/5.0"));
            assertEquals("close", connectionHeader.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesTwoTransientHttpFailures() throws Exception {
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
            JdkFinanceHttpClient client = new JdkFinanceHttpClient(1000, 1000, 1024);
            URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/unstable");

            FinanceHttpResponse response = client.get("EASTMONEY", uri, Collections.emptyMap());

            assertEquals(200, response.getStatus());
            assertEquals(3, attempts.get());
        } finally {
            server.stop(0);
        }
    }
}
