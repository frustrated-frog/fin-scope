package com.finscope.common.exception;

public class ExternalServiceException extends BusinessException {

    public ExternalServiceException(String message) {
        super(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, message);
    }

    public ExternalServiceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ExternalServiceException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
