package com.finscope.rpc.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleLlmClientTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsOpenAiCompatibleChatRequestAndReturnsMessageContent() throws Exception {
        AtomicReference<String> auth = new AtomicReference<String>();
        AtomicReference<String> userAgent = new AtomicReference<String>();
        AtomicReference<String> requestBody = new AtomicReference<String>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            requestBody.set(new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8));
            byte[] body = ("{\"choices\":[{\"message\":{\"content\":\"{\\\"topicName\\\":\\\"Cloudflare 免费基础设施实践\\\"}\"}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(true, baseUrl, "test-key", "test-model", 3000, 0.2);

        String content = client.complete("system prompt", "user prompt");

        assertEquals("Bearer test-key", auth.get());
        assertTrue(userAgent.get().contains("FinScope/0.1"));
        assertTrue(userAgent.get().contains("Mozilla/5.0"));
        assertTrue(requestBody.get().contains("\"model\":\"test-model\""));
        assertTrue(requestBody.get().contains("system prompt"));
        assertTrue(requestBody.get().contains("user prompt"));
        assertEquals("{\"topicName\":\"Cloudflare 免费基础设施实践\"}", content);
    }

    private byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int read;
        while ((read = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
