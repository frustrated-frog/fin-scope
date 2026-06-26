package com.finscope.web.handler;

import com.finscope.common.exception.ErrorCode;
import lombok.Data;

import java.time.Instant;

@Data
public class ApiErrorResponse {
    private boolean success;
    private String code;
    private String message;
    private String error;
    private String traceId;
    private String path;
    private Instant timestamp;

    public static ApiErrorResponse of(ErrorCode errorCode, String message, String traceId, String path) {
        ApiErrorResponse response = new ApiErrorResponse();
        response.setSuccess(false);
        response.setCode(errorCode.getCode());
        response.setMessage(message);
        response.setError(message);
        response.setTraceId(traceId);
        response.setPath(path);
        response.setTimestamp(Instant.now());
        return response;
    }
}
