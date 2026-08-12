package com.finscope.rpc.marketintel;

import java.net.URI;
import java.util.Map;

public interface FinanceHttpClient {
    FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) throws Exception;

    default FinanceHttpResponse postForm(String providerCode, URI uri, String body,
                                         Map<String, String> headers) throws Exception {
        throw new UnsupportedOperationException("当前 HTTP 客户端不支持表单提交");
    }

    default FinanceHttpResponse postJson(String providerCode, URI uri, String body,
                                         Map<String, String> headers) throws Exception {
        throw new UnsupportedOperationException("当前 HTTP 客户端不支持 JSON 提交");
    }

    default FinanceHttpResponse postJson(String providerCode, URI uri, String body,
                                         Map<String, String> headers, int requestTimeoutMs) throws Exception {
        return postJson(providerCode, uri, body, headers);
    }

    default FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers,
                                    int maxResponseBytes) throws Exception {
        return get(providerCode, uri, headers);
    }
}
