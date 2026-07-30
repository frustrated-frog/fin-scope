package com.finscope.rpc.search;

/**
 * 不包含供应商响应正文的搜索调用异常。
 */
public class WebSearchProviderException extends Exception {
    private final String providerCode;
    private final int statusCode;
    private final boolean retryable;

    public WebSearchProviderException(String providerCode, int statusCode,
                                      boolean retryable, String message) {
        super(message);
        this.providerCode = providerCode;
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public String getProviderCode() { return providerCode; }
    public int getStatusCode() { return statusCode; }
    public boolean isRetryable() { return retryable; }
}
