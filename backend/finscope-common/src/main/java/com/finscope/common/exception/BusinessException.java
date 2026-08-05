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

    public BusinessException(BizErrorCode bizErrorCode) {
        super(resolveMessage(bizErrorCode));
        this.errorCode = resolveErrorCode(bizErrorCode);
    }

    public BusinessException(BizErrorCode bizErrorCode, Throwable cause) {
        super(resolveMessage(bizErrorCode), cause);
        this.errorCode = resolveErrorCode(bizErrorCode);
    }

    /**
     * 使用已渲染消息的业务码构造（用于消息中含占位符的场景）。
     *
     * @param bizErrorCode 业务异常码。
     * @param renderedMessage 已通过 {@link BizErrorCode#format(Object...)} 渲染的消息。
     * @param cause 原始异常。
     */
    public BusinessException(BizErrorCode bizErrorCode, String renderedMessage, Throwable cause) {
        super(renderedMessage == null ? resolveMessage(bizErrorCode) : renderedMessage, cause);
        this.errorCode = resolveErrorCode(bizErrorCode);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    private static ErrorCode resolve(ErrorCode errorCode) {
        return errorCode == null ? ErrorCode.INTERNAL_ERROR : errorCode;
    }

    private static ErrorCode resolveErrorCode(BizErrorCode bizErrorCode) {
        return bizErrorCode == null ? ErrorCode.INTERNAL_ERROR : bizErrorCode.getErrorCode();
    }

    private static String resolveMessage(BizErrorCode bizErrorCode) {
        return bizErrorCode == null
                ? ErrorCode.INTERNAL_ERROR.getDefaultMessage() : bizErrorCode.getMessage();
    }
}
