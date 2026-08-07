package com.finscope.rpc.search;

import com.finscope.domain.search.SearchResult;
import com.finscope.domain.search.WebSearchRequest;
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TavilyWebSearchClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void honorsTheSharedProviderDeadline() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search", exchange -> {
            try {
                Thread.sleep(200L);
                byte[] body = "{\"results\":[]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                Thread.sleep(500L);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        TavilyWebSearchClient provider = new TavilyWebSearchClient(true, "test-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/search", 2000);

        long startedAt = System.nanoTime();
        try (ProviderCallDeadline.Scope ignored = ProviderCallDeadline.open(Duration.ofMillis(300))) {
            assertThrows(Exception.class, () -> provider.search(
                    new WebSearchRequest("market", 3, "cn", "zh")));
        }
        long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt);
        org.junit.jupiter.api.Assertions.assertTrue(elapsedMillis < 450L,
                "deadline should stop both response phases, elapsed=" + elapsedMillis);
    }

    @Test
    void exposesProviderMetadataAndRanksParsedResults() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search", exchange -> {
            byte[] body = ("{\"results\":["
                    + "{\"title\":\"英伟达财报\",\"url\":\"https://investor.nvidia.com/a\","
                    + "\"content\":\"收入增长\",\"score\":0.91,\"published_date\":\"2026-07-29\"},"
                    + "{\"title\":\"行业跟踪\",\"url\":\"https://example.com/b\","
                    + "\"content\":\"行业数据\",\"score\":0.72}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        TavilyWebSearchClient provider = new TavilyWebSearchClient(true, "test-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/search", 2000);

        List<SearchResult> results = provider.search(new WebSearchRequest("英伟达 财报", 3, "cn", "zh"));

        assertEquals("TAVILY", provider.providerCode());
        assertEquals(2, results.size());
        assertEquals("TAVILY", results.get(0).getProviderCode());
        assertEquals(1, results.get(0).getProviderRank());
        assertEquals(0.91D, results.get(0).getScore(), 0.001D);
        assertEquals(2, results.get(1).getProviderRank());
    }

    @Test
    void redactsProviderResponseFromErrors() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search", exchange -> {
            byte[] body = "{\"error\":\"invalid test-key\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        TavilyWebSearchClient provider = new TavilyWebSearchClient(true, "test-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/search", 2000);

        WebSearchProviderException error = assertThrows(WebSearchProviderException.class,
                () -> provider.search(new WebSearchRequest("market", 3, "cn", "zh")));

        assertEquals(401, error.getStatusCode());
        assertFalse(error.getMessage().contains("test-key"));
        assertFalse(error.getMessage().contains("invalid"));
    }
}
