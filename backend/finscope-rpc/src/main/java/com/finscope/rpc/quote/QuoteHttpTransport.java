package com.finscope.rpc.quote;

import com.finscope.rpc.acquisition.AcquisitionException;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.acquisition.JdkAcquisitionRuntime;
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;

/** 行情适配器共用的统一 HTTP 传输入口。重试、降级与熔断由 MarketDataGateway 负责。 */
@Component
public class QuoteHttpTransport {
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36 FinScope/0.1";
    private final AcquisitionRuntime acquisitionRuntime;

    public QuoteHttpTransport() {
        this(new JdkAcquisitionRuntime());
    }

    @Autowired
    public QuoteHttpTransport(AcquisitionRuntime acquisitionRuntime) {
        this.acquisitionRuntime = acquisitionRuntime;
    }

    public String get(String provider, URI uri, int timeoutMillis, int maxResponseBytes,
                      Map<String, String> headers, Charset charset) {
        int boundedTimeout = boundedTimeout(timeoutMillis, provider);
        AcquisitionRequest.Builder request = AcquisitionRequest.get(uri)
                .purpose("MARKET_PROVIDER:" + provider)
                .connectTimeoutMs(boundedTimeout)
                .readTimeoutMs(boundedTimeout)
                .deadlineMs(boundedTimeout)
                .maxResponseBytes(maxResponseBytes)
                .maxRetries(0)
                .header("User-Agent", USER_AGENT);
        for (Map.Entry<String, String> header : safeHeaders(headers).entrySet()) {
            request.header(header.getKey(), header.getValue());
        }
        try {
            AcquisitionResponse response = acquisitionRuntime.fetch(request.build());
            return new String(response.getBodyBytes(), charset);
        } catch (AcquisitionException error) {
            String errorType = error.getHttpStatus() == null
                    ? error.getErrorType().name() : "HTTP_" + error.getHttpStatus();
            throw new ProviderContractException(errorType, error.getMessage(), error.isRetryable(), error);
        }
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
