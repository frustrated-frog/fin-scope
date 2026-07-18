package com.finscope.service.financials;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class JdkBrokerResearchHttpClient implements BrokerResearchHttpClient {
    private static final int MAX_REDIRECTS = 3;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final Set<String> allowedHosts;

    public JdkBrokerResearchHttpClient() {
        this(8_000, 20_000, new HashSet<String>(Arrays.asList(
                "reportapi.eastmoney.com", "pdf.dfcfw.com")));
    }

    JdkBrokerResearchHttpClient(int connectTimeoutMs, int readTimeoutMs,
                                Set<String> allowedHosts) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.allowedHosts = new HashSet<String>(allowedHosts);
    }

    @Override
    public Response get(URI uri, Map<String, String> headers, int maxBytes) throws IOException {
        URI current = uri;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            validate(current);
            HttpURLConnection connection = (HttpURLConnection) current.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestMethod("GET");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || redirect == MAX_REDIRECTS) {
                    throw new IOException("研报来源返回了无效重定向");
                }
                current = current.resolve(location);
                continue;
            }
            byte[] body;
            String contentType = connection.getContentType();
            try (InputStream input = status >= 400
                    ? connection.getErrorStream() : connection.getInputStream()) {
                body = input == null ? new byte[0] : readBounded(input, maxBytes);
            } finally {
                connection.disconnect();
            }
            return new Response(status, contentType, current, body);
        }
        throw new IOException("研报来源重定向次数过多");
    }

    private void validate(URI uri) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || !allowedHosts.contains(uri.getHost().toLowerCase())) {
            throw new IOException("研报来源地址不在允许范围内");
        }
    }

    private byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            total += count;
            if (total > maxBytes) {
                throw new IOException("研报来源响应超过大小限制");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
