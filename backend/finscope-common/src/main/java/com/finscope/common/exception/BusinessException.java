package com.finscope.common.exception;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(resolve(errorCode).getDefaultMessage());
        this.errorCode = resolve(errorCode);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = resolve(errorCode);
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode == null ? ErrorCode.INTERNAL_ERROR : errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    private static ErrorCode resolve(ErrorCode errorCode) {
        return errorCode == null ? ErrorCode.INTERNAL_ERROR : errorCode;
    }
}
