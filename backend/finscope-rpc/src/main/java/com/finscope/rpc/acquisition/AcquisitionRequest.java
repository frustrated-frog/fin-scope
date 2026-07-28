package com.finscope.rpc.acquisition;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AcquisitionRequest {
    private final String method;
    private final URI uri;
    private final String purpose;
    private final Map<String, String> headers;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int deadlineMs;
    private final int maxResponseBytes;
    private final int maxRetries;
    private final int retryBackoffMs;
    private final boolean followRedirects;

    private AcquisitionRequest(Builder builder) {
        validateUri(builder.uri);
        if (builder.deadlineMs <= 0 || builder.maxResponseBytes <= 0
                || builder.connectTimeoutMs <= 0 || builder.readTimeoutMs <= 0) {
            throw new IllegalArgumentException("采集超时与响应上限必须大于零");
        }
        if (builder.maxRetries < 0 || builder.maxRetries > 5) {
            throw new IllegalArgumentException("采集重试次数必须在 0 到 5 之间");
        }
        this.method = builder.method;
        this.uri = builder.uri;
        this.purpose = builder.purpose;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.headers));
        this.connectTimeoutMs = builder.connectTimeoutMs;
        this.readTimeoutMs = builder.readTimeoutMs;
        this.deadlineMs = builder.deadlineMs;
        this.maxResponseBytes = builder.maxResponseBytes;
        this.maxRetries = builder.maxRetries;
        this.retryBackoffMs = builder.retryBackoffMs;
        this.followRedirects = builder.followRedirects;
    }

    public static Builder get(URI uri) {
        return new Builder("GET", uri);
    }

    public String getMethod() { return method; }
    public URI getUri() { return uri; }
    public String getPurpose() { return purpose; }
    public Map<String, String> getHeaders() { return headers; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public int getDeadlineMs() { return deadlineMs; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public int getMaxRetries() { return maxRetries; }
    public int getRetryBackoffMs() { return retryBackoffMs; }
    public boolean isFollowRedirects() { return followRedirects; }

    public Map<String, String> auditHeaders() {
        Map<String, String> safe = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (!isSensitive(entry.getKey())) {
                safe.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(safe);
    }

    private static boolean isSensitive(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return normalized.equals("cookie")
                || normalized.equals("authorization")
                || normalized.equals("proxy-authorization")
                || normalized.contains("api-key")
                || normalized.contains("apikey");
    }

    private static void validateUri(URI uri) {
        if (uri == null || uri.getScheme() == null) {
            throw new IllegalArgumentException("采集 URL 不能为空");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme))) {
            throw new IllegalArgumentException("只允许 HTTP 或 HTTPS 采集 URL");
        }
    }

    public static final class Builder {
        private final String method;
        private final URI uri;
        private String purpose = "GENERIC";
        private final Map<String, String> headers = new LinkedHashMap<String, String>();
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 10000;
        private int deadlineMs = 15000;
        private int maxResponseBytes = 8 * 1024 * 1024;
        private int maxRetries = 1;
        private int retryBackoffMs = 250;
        private boolean followRedirects = true;

        private Builder(String method, URI uri) {
            this.method = method;
            this.uri = uri;
        }

        public Builder purpose(String purpose) {
            this.purpose = purpose == null || purpose.trim().isEmpty() ? "GENERIC" : purpose.trim();
            return this;
        }

        public Builder header(String name, String value) {
            if (name != null && !name.trim().isEmpty() && value != null) {
                headers.put(name.trim(), value);
            }
            return this;
        }

        public Builder connectTimeoutMs(int value) { this.connectTimeoutMs = value; return this; }
        public Builder readTimeoutMs(int value) { this.readTimeoutMs = value; return this; }
        public Builder deadlineMs(int value) { this.deadlineMs = value; return this; }
        public Builder maxResponseBytes(int value) { this.maxResponseBytes = value; return this; }
        public Builder maxRetries(int value) { this.maxRetries = value; return this; }
        public Builder retryBackoffMs(int value) { this.retryBackoffMs = value; return this; }
        public Builder followRedirects(boolean value) { this.followRedirects = value; return this; }

        public AcquisitionRequest build() {
            return new AcquisitionRequest(this);
        }
    }
}
