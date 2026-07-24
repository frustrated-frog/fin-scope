package com.finscope.rpc.marketintel;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;

@Component
public class JdkFinanceHttpClient implements FinanceHttpClient {
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36 FinScope/0.1";
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int maxBytes;

    public JdkFinanceHttpClient() {
        this(5000, 10000, 2 * 1024 * 1024);
    }

    public JdkFinanceHttpClient(int connectTimeoutMs, int readTimeoutMs, int maxBytes) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.maxBytes = maxBytes;
    }

    /** @deprecated 请求治理由 MarketDataGateway 统一负责。 */
    @Deprecated
    JdkFinanceHttpClient(int connectTimeoutMs, int readTimeoutMs, int maxBytes, ProviderRequestGuard requestGuard) {
        this(connectTimeoutMs, readTimeoutMs, maxBytes);
    }

    public static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte v : bytes) b.append(String.format("%02x", v));
            return b.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public FinanceHttpResponse get(String provider, URI uri, Map<String, String> headers) throws Exception {
        return getOnce(provider, uri, headers);
    }

    private FinanceHttpResponse getOnce(String provider, URI uri, Map<String, String> headers) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            DeadlineAwareHttpConnection.configure(
                    connection, connectTimeoutMs, readTimeoutMs, provider);
            connection.setRequestProperty("User-Agent", BROWSER_USER_AGENT);
            connection.setRequestProperty("Connection", "close");
            for (Map.Entry<String, String> header : headers.entrySet())
                connection.setRequestProperty(header.getKey(), header.getValue());
            int status = DeadlineAwareHttpConnection.responseCode(
                    connection, readTimeoutMs, provider);
            InputStream input = status >= 200 && status < 300
                    ? DeadlineAwareHttpConnection.inputStream(connection, readTimeoutMs, provider)
                    : connection.getErrorStream();
            String body = new String(DeadlineAwareHttpConnection.readAll(
                    connection, input, readTimeoutMs, maxBytes, provider), StandardCharsets.UTF_8);
            if (status < 200 || status >= 300) throw new ProviderContractException("HTTP_" + status,
                    provider + " returned HTTP " + status, status == 429 || status == 502 || status == 503 || status == 504);
            return new FinanceHttpResponse(status, body, Instant.now(), sha256(body));
        } finally {
            connection.disconnect();
        }
    }

}
