package com.finscope.rpc.acquisition;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkAcquisitionRuntimeTest {
    private HttpServer server;
    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void retriesRetryableStatusOnceAndReturnsAuditableResponse() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer("/retry", exchange -> {
            if (attempts.incrementAndGet() == 1) {
                write(exchange, 503, "暂时不可用");
            } else {
                write(exchange, 200, "稳定正文");
            }
        });
        AcquisitionRequest request = request("/retry")
                .retryBackoffMs(1)
                .build();

        AcquisitionResponse response = new JdkAcquisitionRuntime().fetch(request);

        assertEquals("稳定正文", response.getBodyText());
        assertEquals(2, response.getAttemptCount());
        assertEquals(2, attempts.get());
        assertEquals("0825aac279833f484f28d2a2cdf7dbb141bc50930ae5933eee0178dee15ec888",
                response.getBodySha256());
        assertTrue(response.getDurationMs() >= 0);
    }

    @Test
    void doesNotRetryClientErrors() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer("/missing", exchange -> {
            attempts.incrementAndGet();
            write(exchange, 404, "不存在");
        });

        AcquisitionException error = assertThrows(AcquisitionException.class,
                () -> new JdkAcquisitionRuntime().fetch(request("/missing").build()));

        assertEquals(AcquisitionErrorType.HTTP_CLIENT_ERROR, error.getErrorType());
        assertEquals(Integer.valueOf(404), error.getHttpStatus());
        assertEquals(1, attempts.get());
    }

    @Test
    void rejectsResponseLargerThanConfiguredLimit() throws Exception {
        startServer("/large", exchange -> write(exchange, 200, "123456789"));

        AcquisitionException error = assertThrows(AcquisitionException.class,
                () -> new JdkAcquisitionRuntime().fetch(
                        request("/large").maxResponseBytes(8).build()));

        assertEquals(AcquisitionErrorType.RESPONSE_TOO_LARGE, error.getErrorType());
        assertEquals(false, error.isRetryable());
    }

    @Test
    void classifiesReadTimeoutAndHonorsRetryBudget() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer("/slow", exchange -> {
            attempts.incrementAndGet();
            try {
                Thread.sleep(200);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            write(exchange, 200, "太慢");
        });
        AcquisitionRequest request = request("/slow")
                .connectTimeoutMs(50)
                .readTimeoutMs(50)
                .deadlineMs(500)
                .maxRetries(1)
                .retryBackoffMs(1)
                .build();

        AcquisitionException error = assertThrows(AcquisitionException.class,
                () -> new JdkAcquisitionRuntime().fetch(request));

        assertEquals(AcquisitionErrorType.TIMEOUT, error.getErrorType());
        assertEquals(2, attempts.get());
    }

    private AcquisitionRequest.Builder request(String path) {
        URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + path);
        return AcquisitionRequest.get(uri).purpose("TEST");
    }

    private void startServer(String path, com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext(path, handler);
        server.start();
    }

    private static void write(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        try {
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } finally {
            exchange.close();
        }
    }
}
