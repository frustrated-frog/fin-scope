package com.finscope.rpc.acquisition;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JdkAcquisitionRuntime implements AcquisitionRuntime {
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 FinScope-Acquisition/0.1";
    private static final Logger log = LoggerFactory.getLogger(JdkAcquisitionRuntime.class);
    private final List<AcquisitionObserver> observers;

    public JdkAcquisitionRuntime() {
        this(Collections.<AcquisitionObserver>emptyList());
    }

    @Autowired
    public JdkAcquisitionRuntime(List<AcquisitionObserver> observers) {
        this.observers = observers == null
                ? Collections.<AcquisitionObserver>emptyList()
                : Collections.unmodifiableList(observers);
    }

    @Override
    public AcquisitionResponse fetch(AcquisitionRequest request) {
        long startedNanos = System.nanoTime();
        long deadlineNanos = startedNanos + request.getDeadlineMs() * 1_000_000L;
        AcquisitionException lastError = null;

        for (int attempt = 1; attempt <= request.getMaxRetries() + 1; attempt++) {
            try {
                AcquisitionResponse response = fetchOnce(request, attempt, startedNanos, deadlineNanos);
                notifySuccess(request, response);
                return response;
            } catch (AcquisitionException error) {
                lastError = error;
                if (!error.isRetryable() || attempt > request.getMaxRetries()) {
                    throw error;
                }
                sleepBeforeRetry(request, attempt, deadlineNanos);
            }
        }
        throw lastError == null
                ? new AcquisitionException(AcquisitionErrorType.CONNECTION_ERROR,
                "采集请求未执行", false, null)
                : lastError;
    }

    private AcquisitionResponse fetchOnce(AcquisitionRequest request, int attempt,
                                          long startedNanos, long deadlineNanos) {
        HttpURLConnection connection = null;
        try {
            int remainingMs = remainingMillis(deadlineNanos);
            connection = (HttpURLConnection) request.getUri().toURL().openConnection();
            connection.setRequestMethod(request.getMethod());
            connection.setConnectTimeout(Math.min(request.getConnectTimeoutMs(), remainingMs));
            connection.setReadTimeout(Math.min(request.getReadTimeoutMs(), remainingMs));
            connection.setInstanceFollowRedirects(request.isFollowRedirects());
            connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);
            connection.setRequestProperty("Accept-Encoding", "identity");
            for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] bytes = readBounded(stream, request.getMaxResponseBytes());
            if (status < 200 || status >= 300) {
                throw httpError(status);
            }
            if (bytes.length == 0) {
                throw new AcquisitionException(AcquisitionErrorType.EMPTY_RESPONSE,
                        "采集响应为空", false, status);
            }

            String contentType = connection.getContentType();
            ResponseTextDecoder.DecodedText decoded = ResponseTextDecoder.decode(
                    bytes, contentType, isTextual(contentType));
            return new AcquisitionResponse(
                    request.getUri(), connection.getURL().toURI(), status,
                    responseHeaders(connection), bytes, decoded.getText(), contentType,
                    decoded.getCharsetName(), sha256(bytes), attempt,
                    elapsedMillis(startedNanos), Instant.now());
        } catch (AcquisitionException error) {
            throw error;
        } catch (SocketTimeoutException error) {
            throw new AcquisitionException(AcquisitionErrorType.TIMEOUT,
                    "采集请求超时", true, null, error);
        } catch (Exception error) {
            throw new AcquisitionException(AcquisitionErrorType.CONNECTION_ERROR,
                    "采集连接失败：" + safeMessage(error), true, null, error);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private byte[] readBounded(InputStream stream, int maxBytes) throws IOException {
        if (stream == null) {
            return new byte[0];
        }
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new AcquisitionException(AcquisitionErrorType.RESPONSE_TOO_LARGE,
                            "采集响应超过大小限制：" + maxBytes + " bytes", false, null);
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private AcquisitionException httpError(int status) {
        if (status == 429) {
            return new AcquisitionException(AcquisitionErrorType.HTTP_RATE_LIMITED,
                    "采集端点触发限流，HTTP 429", true, status);
        }
        if (status == 502 || status == 503 || status == 504) {
            return new AcquisitionException(AcquisitionErrorType.HTTP_SERVER_ERROR,
                    "采集端点暂时不可用，HTTP " + status, true, status);
        }
        if (status >= 500) {
            return new AcquisitionException(AcquisitionErrorType.HTTP_SERVER_ERROR,
                    "采集端点服务端错误，HTTP " + status, false, status);
        }
        return new AcquisitionException(AcquisitionErrorType.HTTP_CLIENT_ERROR,
                "采集请求被拒绝，HTTP " + status, false, status);
    }

    private Map<String, String> responseHeaders(HttpURLConnection connection) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                result.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private void sleepBeforeRetry(AcquisitionRequest request, int attempt, long deadlineNanos) {
        long delay = (long) request.getRetryBackoffMs() * attempt;
        int remaining = remainingMillis(deadlineNanos);
        if (delay >= remaining) {
            throw new AcquisitionException(AcquisitionErrorType.TIMEOUT,
                    "采集总时间预算已耗尽", false, null);
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AcquisitionException(AcquisitionErrorType.CONNECTION_ERROR,
                    "采集重试等待被中断", false, null, error);
        }
    }

    private int remainingMillis(long deadlineNanos) {
        long remaining = (deadlineNanos - System.nanoTime()) / 1_000_000L;
        if (remaining <= 0) {
            throw new AcquisitionException(AcquisitionErrorType.TIMEOUT,
                    "采集总时间预算已耗尽", false, null);
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, remaining));
    }

    private boolean isTextual(String contentType) {
        if (contentType == null) {
            return true;
        }
        String normalized = contentType.toLowerCase();
        return normalized.startsWith("text/")
                || normalized.contains("json")
                || normalized.contains("xml")
                || normalized.contains("html");
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder();
            for (byte item : digest) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (Exception error) {
            throw new IllegalStateException("无法计算采集响应哈希", error);
        }
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private void notifySuccess(AcquisitionRequest request, AcquisitionResponse response) {
        for (AcquisitionObserver observer : observers) {
            try {
                observer.onSuccess(request, response);
            } catch (RuntimeException error) {
                log.warn("采集观察器执行失败 purpose={} url={} error={}",
                        request.getPurpose(), request.getUri(), safeMessage(error));
            }
        }
    }
}
