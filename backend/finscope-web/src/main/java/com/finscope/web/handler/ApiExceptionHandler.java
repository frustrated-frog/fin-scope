package com.finscope.web.handler;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.web.config.RequestLoggingFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return buildResponse(ex.getErrorCode(), safeMessage(ex, ex.getErrorCode()), ex, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ErrorCode errorCode = isNotFound(ex.getMessage()) ? ErrorCode.NOT_FOUND : ErrorCode.BAD_REQUEST;
        return buildResponse(errorCode, safeMessage(ex, errorCode), ex, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.BAD_REQUEST, "Invalid request body", ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), ex, request);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(ErrorCode errorCode,
                                                           String message,
                                                           Exception ex,
                                                           HttpServletRequest request) {
        HttpStatus status = statusFor(errorCode);
        String traceId = currentTraceId(request);
        String path = request == null ? "" : request.getRequestURI();
        if (status.is5xxServerError()) {
            log.error("接口错误 code={} status={} path={} traceId={} message={}",
                    errorCode.getCode(), status.value(), path, traceId, ex.getMessage(), ex);
        } else {
            log.warn("接口错误 code={} status={} path={} traceId={} message={}",
                    errorCode.getCode(), status.value(), path, traceId, ex.getMessage());
        }
        return ResponseEntity.status(status).body(ApiErrorResponse.of(errorCode, message, traceId, path));
    }

    private HttpStatus statusFor(ErrorCode errorCode) {
        switch (errorCode) {
            case BAD_REQUEST:
                return HttpStatus.BAD_REQUEST;
            case NOT_FOUND:
                return HttpStatus.NOT_FOUND;
            case CONFLICT:
                return HttpStatus.CONFLICT;
            case EXTERNAL_SERVICE_ERROR:
                return HttpStatus.BAD_GATEWAY;
            case INTERNAL_ERROR:
                return HttpStatus.INTERNAL_SERVER_ERROR;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private String safeMessage(Exception ex, ErrorCode errorCode) {
        if (ex.getMessage() == null || ex.getMessage().trim().isEmpty()) {
            return errorCode.getDefaultMessage();
        }
        return ex.getMessage();
    }

    private boolean isNotFound(String message) {
        return message != null && message.toLowerCase().contains("not found");
    }

    private String currentTraceId(HttpServletRequest request) {
        String traceId = MDC.get(RequestLoggingFilter.TRACE_ID);
        if (traceId != null && !traceId.trim().isEmpty()) {
            return traceId;
        }
        if (request != null) {
            String header = request.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
            if (header != null && !header.trim().isEmpty()) {
                return header.trim();
            }
        }
        return "";
    }
}
