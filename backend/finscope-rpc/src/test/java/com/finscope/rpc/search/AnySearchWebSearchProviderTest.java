package com.finscope.rpc.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.search.SearchResult;
import com.finscope.domain.search.WebSearchRequest;
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnySearchWebSearchProviderTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void honorsTheSharedProviderDeadline() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/search", exchange -> {
            try {
                Thread.sleep(200L);
                byte[] body = "{\"data\":[]}".getBytes(StandardCharsets.UTF_8);
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

        long startedAt = System.nanoTime();
        try (ProviderCallDeadline.Scope ignored = ProviderCallDeadline.open(Duration.ofMillis(300))) {
            assertThrows(Exception.class, () -> provider("secret-key").search(
                    new WebSearchRequest("market", 3, "cn", "zh")));
        }
        long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt);
        assertTrue(elapsedMillis < 450L, "deadline should stop both response phases, elapsed=" + elapsedMillis);
    }

    @Test
    void sendsGeneralSearchRequestAndKeepsResultsWithoutScores() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<String>();
        AtomicReference<String> requestBody = new AtomicReference<String>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/search", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(readAll(exchange.getRequestBody()));
            byte[] body = ("{\"data\":[{\"title\":\"NVIDIA IR\","
                    + "\"url\":\"https://investor.nvidia.com/news\","
                    + "\"snippet\":\"official release\",\"content\":\"full release\"}],"
                    + "\"metadata\":{\"total_results\":1,\"search_time_ms\":55}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        AnySearchWebSearchProvider provider = provider("secret-key");

        List<SearchResult> results = provider.search(
                new WebSearchRequest("NVIDIA latest news", 5, "intl", "en"));

        assertEquals("Bearer secret-key", authorization.get());
        JsonNode sent = new ObjectMapper().readTree(requestBody.get());
        assertEquals("general.general", sent.path("tag").asText());
        assertEquals("json", sent.path("format").asText());
        assertEquals("intl", sent.path("zone").asText());
        assertEquals("en", sent.path("language").asText());
        assertEquals(5, sent.path("max_results").asInt());
        assertEquals("ANYSEARCH", results.get(0).getProviderCode());
        assertEquals(1, results.get(0).getProviderRank());
        assertEquals("full release", results.get(0).getContent());
        assertNull(results.get(0).getScore());
    }

    @Test
    void fallsBackToSnippetWhenContentIsEmpty() throws Exception {
        startJsonServer(200, "{\"data\":[{\"title\":\"News\",\"url\":\"https://example.com/n\","
                + "\"snippet\":\"summary\",\"content\":\"\"}]}");

        List<SearchResult> results = provider("secret-key").search(
                new WebSearchRequest("market news", 3, "cn", "zh"));

        assertEquals("summary", results.get(0).getContent());
    }

    @Test
    void redactsCredentialsAndResponseBodyFromErrors() throws Exception {
        startJsonServer(401, "{\"error\":\"invalid secret-key\"}");

        WebSearchProviderException error = assertThrows(WebSearchProviderException.class,
                () -> provider("secret-key").search(new WebSearchRequest("market", 3, "cn", "zh")));

        assertEquals("ANYSEARCH", error.getProviderCode());
        assertEquals(401, error.getStatusCode());
        assertFalse(error.isRetryable());
        assertTrue(error.getMessage().contains("401"));
        assertFalse(error.getMessage().contains("secret-key"));
        assertFalse(error.getMessage().contains("invalid"));
    }

    private AnySearchWebSearchProvider provider(String apiKey) {
        return new AnySearchWebSearchProvider(true, apiKey,
                "http://127.0.0.1:" + server.getAddress().getPort(), 2000, 262144);
    }

    private void startJsonServer(int status, String json) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/search", exchange -> {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    private static String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int read;
        while ((read = input.read(chunk)) >= 0) buffer.write(chunk, 0, read);
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
