package com.finscope.common.exception;

public class BusinessConflictException extends BusinessException {

    public BusinessConflictException(String message) {
        super(ErrorCode.BUSINESS_CONFLICT, message);
    }

    public BusinessConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessConflictException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
