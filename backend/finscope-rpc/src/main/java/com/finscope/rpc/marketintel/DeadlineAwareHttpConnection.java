package com.finscope.rpc.marketintel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;

/** 行情 HttpURLConnection 的统一截止时间与响应读取策略。 */
public final class DeadlineAwareHttpConnection {
    private DeadlineAwareHttpConnection() { }

    public static void configure(HttpURLConnection connection,
                                 int connectTimeoutMillis,
                                 int readTimeoutMillis,
                                 String provider) {
        connection.setConnectTimeout(bounded(connectTimeoutMillis, provider));
        connection.setReadTimeout(bounded(readTimeoutMillis, provider));
    }

    public static int responseCode(HttpURLConnection connection,
                                   int readTimeoutMillis,
                                   String provider) throws IOException {
        connection.setReadTimeout(bounded(readTimeoutMillis, provider));
        try {
            return connection.getResponseCode();
        } catch (SocketTimeoutException error) {
            throw timeout(provider, error);
        }
    }

    public static InputStream inputStream(HttpURLConnection connection,
                                          int readTimeoutMillis,
                                          String provider) throws IOException {
        connection.setReadTimeout(bounded(readTimeoutMillis, provider));
        try {
            return connection.getInputStream();
        } catch (SocketTimeoutException error) {
            throw timeout(provider, error);
        }
    }

    /** 每次 read 前重新收紧超时，保证慢速分块无法延长整体 deadline。 */
    public static byte[] readAll(HttpURLConnection connection, InputStream input,
                                 int readTimeoutMillis, int maxBytes,
                                 String provider) throws IOException {
        if (input == null) return new byte[0];
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            while (true) {
                connection.setReadTimeout(bounded(readTimeoutMillis, provider));
                final int read;
                try {
                    read = in.read(buffer);
                } catch (SocketTimeoutException error) {
                    throw timeout(provider, error);
                }
                if (read < 0) break;
                total += read;
                if (maxBytes > 0 && total > maxBytes) {
                    throw new ProviderContractException("RESPONSE_TOO_LARGE",
                            "finance response exceeds limit", false);
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static int bounded(int configuredMillis, String provider) {
        long remaining = ProviderCallDeadline.remainingMillis();
        if (remaining <= 0L) throw timeout(provider, null);
        return (int) Math.max(1L, Math.min((long) configuredMillis, remaining));
    }

    private static ProviderContractException timeout(String provider, Throwable cause) {
        String message = provider + " exceeded provider deadline";
        return cause == null
                ? new ProviderContractException("TIMEOUT", message, true)
                : new ProviderContractException("TIMEOUT", message, true, cause);
    }
}
