package com.finscope.rpc.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.news.ai.NewsAiProviderConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NewsAiLunaClientTest {
    @Test
    void sendsOneResponsesRequestAndRetainsUsageEvenForIncompleteOutput() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/responses", exchange -> {
            calls.incrementAndGet();
            var request = new ObjectMapper().readTree(exchange.getRequestBody());
            assertEquals("gpt-6-luna", request.path("model").asText());
            assertEquals("none", request.path("reasoning").path("effort").asText());
            assertFalse(request.path("store").asBoolean());
            assertEquals("json_schema", request.path("text").path("format").path("type").asText());
            byte[] response = "{\"status\":\"incomplete\",\"usage\":{\"input_tokens\":120,\"output_tokens\":30},\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"{}\"}]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var client = client(server.getAddress().getPort());
            var prepared = client.prepare("system", "中文金额5亿元", 200, Map.of("type", "object"));
            assertTrue(prepared.getInputTokenUpperBound() >= prepared.getBody().length);
            var reply = client.complete(prepared);
            assertFalse(reply.isCompleted()); assertEquals(120L, reply.getInputTokens()); assertEquals(30L, reply.getOutputTokens());
            assertEquals(1, calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotAutomaticallyRetryRateLimitsOrPrintTheirBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/responses", exchange -> {
            calls.incrementAndGet(); byte[] text = "private-provider-body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, text.length); exchange.getResponseBody().write(text); exchange.close();
        });
        server.start();
        try {
            var client = client(server.getAddress().getPort());
            var error = assertThrows(IllegalStateException.class, () -> client.complete(client.prepare("s", "u", 100, Map.of())));
            assertFalse(error.getMessage().contains("private-provider-body")); assertEquals(1, calls.get());
        } finally {
            server.stop(0);
        }
    }

    private NewsAiLunaClient client(int port) {
        var config = new NewsAiProviderConfig(); config.setBaseUrl("http://127.0.0.1:" + port + "/v1"); config.setApiKey("test-key");
        var client = new NewsAiLunaClient(); ReflectionTestUtils.setField(client, "config", config);
        ReflectionTestUtils.setField(client, "mapper", new ObjectMapper()); return client;
    }
}
