package com.finscope.rpc.marketintel;

public class ProviderContractException extends RuntimeException {
    private final String errorType;
    private final boolean retryable;

    public ProviderContractException(String errorType, String message, boolean retryable) {
        super(message);
        this.errorType = errorType;
        this.retryable = retryable;
    }

    public ProviderContractException(String errorType, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.retryable = retryable;
    }

    public String getErrorType() {
        return errorType;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
