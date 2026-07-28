package com.finscope.rpc.marketintel;

import com.finscope.rpc.acquisition.AcquisitionException;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.acquisition.JdkAcquisitionRuntime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Map;

@Component
public class JdkFinanceHttpClient implements FinanceHttpClient {
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36 FinScope/0.1";
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int maxBytes;
    private final AcquisitionRuntime acquisitionRuntime;

    public JdkFinanceHttpClient() {
        this(new JdkAcquisitionRuntime(), 5000, 10000, 2 * 1024 * 1024);
    }

    @Autowired
    public JdkFinanceHttpClient(AcquisitionRuntime acquisitionRuntime) {
        this(acquisitionRuntime, 5000, 10000, 2 * 1024 * 1024);
    }

    public JdkFinanceHttpClient(int connectTimeoutMs, int readTimeoutMs, int maxBytes) {
        this(new JdkAcquisitionRuntime(), connectTimeoutMs, readTimeoutMs, maxBytes);
    }

    JdkFinanceHttpClient(AcquisitionRuntime acquisitionRuntime,
                         int connectTimeoutMs, int readTimeoutMs, int maxBytes) {
        this.acquisitionRuntime = acquisitionRuntime;
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
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("无法计算行情响应哈希", error);
        }
    }

    @Override
    public FinanceHttpResponse get(String provider, URI uri, Map<String, String> headers) throws Exception {
        try {
            AcquisitionRequest.Builder request = AcquisitionRequest.get(uri)
                    .purpose("MARKET_PROVIDER:" + provider)
                    .connectTimeoutMs(boundedTimeout(connectTimeoutMs, provider))
                    .readTimeoutMs(boundedTimeout(readTimeoutMs, provider))
                    .deadlineMs(deadlineMillis(provider))
                    .maxResponseBytes(maxBytes)
                    .maxRetries(0)
                    .header("User-Agent", BROWSER_USER_AGENT);
            for (Map.Entry<String, String> header : safeHeaders(headers).entrySet()) {
                request.header(header.getKey(), header.getValue());
            }
            AcquisitionResponse response = acquisitionRuntime.fetch(request.build());
            return new FinanceHttpResponse(response.getHttpStatus(), response.getBodyText(),
                    response.getFetchedAt(), response.getBodySha256());
        } catch (AcquisitionException error) {
            String errorType = error.getHttpStatus() == null
                    ? error.getErrorType().name() : "HTTP_" + error.getHttpStatus();
            throw new ProviderContractException(errorType, error.getMessage(), error.isRetryable(), error);
        }
    }

    private int deadlineMillis(String provider) {
        long remaining = ProviderCallDeadline.remainingMillis();
        if (remaining <= 0L) {
            throw new ProviderContractException("TIMEOUT", provider + " exceeded provider deadline", true);
        }
        long configured = (long) connectTimeoutMs + (long) readTimeoutMs;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, Math.min(configured, remaining)));
    }

    private int boundedTimeout(int configured, String provider) {
        long remaining = ProviderCallDeadline.remainingMillis();
        if (remaining <= 0L) {
            throw new ProviderContractException("TIMEOUT", provider + " exceeded provider deadline", true);
        }
        return (int) Math.max(1L, Math.min((long) configured, remaining));
    }

    private Map<String, String> safeHeaders(Map<String, String> headers) {
        return headers == null ? Collections.<String, String>emptyMap() : headers;
    }
}
