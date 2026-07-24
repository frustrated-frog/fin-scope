package com.finscope.rpc.quote;

import com.finscope.rpc.marketintel.DeadlineAwareHttpConnection;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 基金 Provider 共用的 HTTP 传输层，统一超时、状态码和请求头策略。 */
final class EastmoneyFundHttpClient {
    private EastmoneyFundHttpClient() { }

    static String get(String urlText, int timeoutMillis) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod("GET");
        DeadlineAwareHttpConnection.configure(
                connection, timeoutMillis, timeoutMillis, "EASTMONEY_FUND");
        connection.setRequestProperty("Referer", "https://fund.eastmoney.com");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 FinScope/0.1");
        try {
            int status = DeadlineAwareHttpConnection.responseCode(
                    connection, timeoutMillis, "EASTMONEY_FUND");
            if (status < 200 || status >= 300) {
                throw new IOException("Eastmoney fund HTTP " + status);
            }
            InputStream input = DeadlineAwareHttpConnection.inputStream(
                    connection, timeoutMillis, "EASTMONEY_FUND");
            return new String(DeadlineAwareHttpConnection.readAll(
                    connection, input, timeoutMillis, 0, "EASTMONEY_FUND"),
                    StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }
}
