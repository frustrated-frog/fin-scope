package com.finscope.rpc.quote;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** 基金 Provider 共用的 HTTP 传输层，统一超时、状态码和请求头策略。 */
final class EastmoneyFundHttpClient {
    private EastmoneyFundHttpClient() { }

    static String get(String urlText, int timeoutMillis) throws Exception {
        return get(new QuoteHttpTransport(), urlText, timeoutMillis);
    }

    static String get(QuoteHttpTransport transport, String urlText, int timeoutMillis) {
        return transport.get("EASTMONEY_FUND", URI.create(urlText), timeoutMillis,
                4 * 1024 * 1024,
                Collections.singletonMap("Referer", "https://fund.eastmoney.com"),
                StandardCharsets.UTF_8);
    }
}
