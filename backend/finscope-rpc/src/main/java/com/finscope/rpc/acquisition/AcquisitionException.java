package com.finscope.rpc.acquisition;

public class AcquisitionException extends RuntimeException {
    private final AcquisitionErrorType errorType;
    private final boolean retryable;
    private final Integer httpStatus;

    public AcquisitionException(AcquisitionErrorType errorType, String message,
                                boolean retryable, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

    public AcquisitionException(AcquisitionErrorType errorType, String message,
                                boolean retryable, Integer httpStatus) {
        this(errorType, message, retryable, httpStatus, null);
    }

    public AcquisitionErrorType getErrorType() {
        return errorType;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
