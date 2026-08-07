package com.finscope.rpc.marketintel;

import java.net.URI;
import java.util.Map;

public interface FinanceHttpClient {
    FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) throws Exception;

    default FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers,
                                    int maxResponseBytes) throws Exception {
        return get(providerCode, uri, headers);
    }
}
