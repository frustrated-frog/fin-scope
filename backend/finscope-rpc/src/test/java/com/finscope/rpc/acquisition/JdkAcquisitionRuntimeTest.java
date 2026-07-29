package com.finscope.rpc.acquisition;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;

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

    @Test
    void notifiesObserverAfterSuccessfulAcquisition() throws Exception {
        startServer("/observed", exchange -> write(exchange, 200, "可审计正文"));
        AtomicReference<AcquisitionRequest> observedRequest = new AtomicReference<AcquisitionRequest>();
        AtomicReference<AcquisitionResponse> observedResponse = new AtomicReference<AcquisitionResponse>();
        AcquisitionObserver observer = (request, response) -> {
            observedRequest.set(request);
            observedResponse.set(response);
        };

        AcquisitionResponse response = new JdkAcquisitionRuntime(
                java.util.Collections.singletonList(observer)).fetch(request("/observed").build());

        assertEquals("TEST", observedRequest.get().getPurpose());
        assertEquals(response.getBodySha256(), observedResponse.get().getBodySha256());
    }

    @Test
    void observerFailureDoesNotBreakSuccessfulAcquisition() throws Exception {
        startServer("/observer-failure", exchange -> write(exchange, 200, "主链路成功"));
        AcquisitionObserver observer = (request, response) -> {
            throw new IllegalStateException("快照磁盘临时不可用");
        };

        AcquisitionResponse response = new JdkAcquisitionRuntime(
                java.util.Collections.singletonList(observer)).fetch(request("/observer-failure").build());

        assertEquals("主链路成功", response.getBodyText());
    }

    @Test
    void sendsBoundedPostBodyWithDeclaredContentType() throws Exception {
        AtomicReference<String> method = new AtomicReference<String>();
        AtomicReference<String> contentType = new AtomicReference<String>();
        AtomicReference<String> body = new AtomicReference<String>();
        startServer("/post", exchange -> {
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(read(exchange.getRequestBody()));
            write(exchange, 200, "ok");
        });
        URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/post");

        AcquisitionResponse response = new JdkAcquisitionRuntime().fetch(
                AcquisitionRequest.post(uri, "stock=000001&query=公告", "application/x-www-form-urlencoded")
                        .purpose("TEST_POST")
                        .build());

        assertEquals("ok", response.getBodyText());
        assertEquals("POST", method.get());
        assertEquals("application/x-www-form-urlencoded", contentType.get());
        assertEquals("stock=000001&query=公告", body.get());
    }

    @Test
    void honorsProviderDeadlineAcrossAcquisitionRequestBudget() throws Exception {
        startServer("/provider-deadline", exchange -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            write(exchange, 200, "太慢");
        });
        AcquisitionRequest request = request("/provider-deadline")
                .readTimeoutMs(2_000).deadlineMs(2_000).maxRetries(0).build();

        AcquisitionException error;
        try (ProviderCallDeadline.Scope ignored = ProviderCallDeadline.open(Duration.ofMillis(60))) {
            error = assertThrows(AcquisitionException.class,
                    () -> new JdkAcquisitionRuntime().fetch(request));
        }

        assertEquals(AcquisitionErrorType.TIMEOUT, error.getErrorType());
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

    private static String read(InputStream input) throws IOException {
        byte[] buffer = new byte[1024];
        StringBuilder value = new StringBuilder();
        int count;
        while ((count = input.read(buffer)) >= 0) {
            value.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
        }
        return value.toString();
    }
}
