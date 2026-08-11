package com.finscope.rpc.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.search.SearchResult;
import com.finscope.domain.search.WebSearchRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FirecrawlWebSearchProviderTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void searchesTheWebAndMapsFirecrawlResults() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<String>();
        AtomicReference<String> requestBody = new AtomicReference<String>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v2/search", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(readAll(exchange.getRequestBody()));
            byte[] body = ("{\"success\":true,\"data\":{\"web\":[{"
                    + "\"title\":\"光模块产业链\",\"url\":\"https://example.com/chain\","
                    + "\"description\":\"上游芯片与下游云厂商\","
                    + "\"markdown\":\"完整产业链资料\"}]}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        List<SearchResult> results = provider("secret-key").search(
                new WebSearchRequest("光模块 产业链", 5, "cn", "zh"));

        assertEquals("Bearer secret-key", authorization.get());
        JsonNode sent = new ObjectMapper().readTree(requestBody.get());
        assertEquals("光模块 产业链", sent.path("query").asText());
        assertEquals(5, sent.path("limit").asInt());
        assertEquals("FIRECRAWL", results.get(0).getProviderCode());
        assertEquals("完整产业链资料", results.get(0).getContent());
        assertEquals("example.com", results.get(0).getSourceDomain());
    }

    @Test
    void redactsCredentialsAndProviderBodyFromErrors() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v2/search", exchange -> {
            byte[] body = "{\"error\":\"invalid secret-key\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        WebSearchProviderException error = assertThrows(WebSearchProviderException.class,
                () -> provider("secret-key").search(new WebSearchRequest("market", 3, "cn", "zh")));

        assertEquals(401, error.getStatusCode());
        assertFalse(error.getMessage().contains("secret-key"));
        assertFalse(error.getMessage().contains("invalid"));
    }

    private FirecrawlWebSearchProvider provider(String apiKey) {
        return new FirecrawlWebSearchProvider(true, apiKey,
                "http://127.0.0.1:" + server.getAddress().getPort(), 2000, 262144);
    }

    private static String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int read;
        while ((read = input.read(chunk)) >= 0) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
