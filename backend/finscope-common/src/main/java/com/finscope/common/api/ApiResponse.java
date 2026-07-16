package com.finscope.common.api;

import com.finscope.common.exception.ErrorCode;

import java.time.Instant;

public class ApiResponse<T> {
    private boolean success;
    private String code;
    private String message;
    private T data;
    private String traceId;
    private Instant timestamp;

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return build(true, ErrorCode.SUCCESS, ErrorCode.SUCCESS.getDefaultMessage(), data, traceId);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message, String traceId) {
        ErrorCode resolved = errorCode == null ? ErrorCode.INTERNAL_ERROR : errorCode;
        String resolvedMessage = message == null || message.trim().isEmpty()
                ? resolved.getDefaultMessage() : message.trim();
        return build(false, resolved, resolvedMessage, null, traceId);
    }

    private static <T> ApiResponse<T> build(boolean success,
                                            ErrorCode errorCode,
                                            String message,
                                            T data,
                                            String traceId) {
        ApiResponse<T> response = new ApiResponse<T>();
        response.setSuccess(success);
        response.setCode(errorCode.getCode());
        response.setMessage(message);
        response.setData(data);
        response.setTraceId(traceId == null ? "" : traceId);
        response.setTimestamp(Instant.now());
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
