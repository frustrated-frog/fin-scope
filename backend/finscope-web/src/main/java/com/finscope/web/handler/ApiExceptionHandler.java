package com.finscope.web.handler;

import com.finscope.common.api.ApiResponse;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.web.config.RequestLoggingFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex,
                                                            HttpServletRequest request) {
        return buildResponse(ex.getErrorCode(), businessMessage(ex), ex, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.REQUEST_PARAMETER_MISSING,
                ErrorCode.REQUEST_PARAMETER_MISSING.getDefaultMessage(), ex, request);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(
            Exception ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.REQUEST_PARAMETER_INVALID,
                ErrorCode.REQUEST_PARAMETER_INVALID.getDefaultMessage(), ex, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.REQUEST_BODY_INVALID,
                ErrorCode.REQUEST_BODY_INVALID.getDefaultMessage(), ex, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.REQUEST_PARAMETER_INVALID,
                fieldErrorMessage(ex.getBindingResult().getFieldErrors()), ex, request);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBinding(
            BindException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.REQUEST_PARAMETER_INVALID,
                fieldErrorMessage(ex.getBindingResult().getFieldErrors()), ex, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.REQUEST_PARAMETER_INVALID,
                constraintMessage(ex.getConstraintViolations()), ex, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.REQUEST_METHOD_NOT_SUPPORTED,
                ErrorCode.REQUEST_METHOD_NOT_SUPPORTED.getDefaultMessage(), ex, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED,
                ErrorCode.MEDIA_TYPE_NOT_SUPPORTED.getDefaultMessage(), ex, request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(
            DataAccessException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.DATABASE_ERROR,
                ErrorCode.DATABASE_ERROR.getDefaultMessage(), ex, request);
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleAsyncTimeout(
            AsyncRequestTimeoutException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.ASYNC_TASK_ERROR,
                ErrorCode.ASYNC_TASK_ERROR.getDefaultMessage(), ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(), ex, request);
    }

    private ResponseEntity<ApiResponse<Void>> buildResponse(ErrorCode errorCode,
                                                            String message,
                                                            Exception ex,
                                                            HttpServletRequest request) {
        String traceId = currentTraceId(request);
        String path = request == null ? "" : request.getRequestURI();
        if (errorCode.getHttpStatus() >= 500) {
            log.error("接口异常 code={} status={} path={} message={}",
                    errorCode.getCode(), errorCode.getHttpStatus(), path, ex.getMessage(), ex);
        } else {
            log.warn("接口异常 code={} status={} path={} message={}",
                    errorCode.getCode(), errorCode.getHttpStatus(), path, ex.getMessage());
        }
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.failure(errorCode, message, traceId));
    }

    private String businessMessage(BusinessException ex) {
        if (ex.getMessage() == null || ex.getMessage().trim().isEmpty()) {
            return ex.getErrorCode().getDefaultMessage();
        }
        return ex.getMessage().trim();
    }

    private String fieldErrorMessage(List<FieldError> errors) {
        if (errors == null || errors.isEmpty()) {
            return ErrorCode.REQUEST_PARAMETER_INVALID.getDefaultMessage();
        }
        List<FieldError> sorted = new ArrayList<FieldError>(errors);
        Collections.sort(sorted, Comparator.comparing(FieldError::getField));
        List<String> messages = new ArrayList<String>();
        for (FieldError error : sorted) {
            messages.add(error.getField() + "：" + safeValidationMessage(error.getDefaultMessage()));
        }
        return String.join("；", messages);
    }

    private String constraintMessage(Set<ConstraintViolation<?>> violations) {
        if (violations == null || violations.isEmpty()) {
            return ErrorCode.REQUEST_PARAMETER_INVALID.getDefaultMessage();
        }
        List<String> messages = new ArrayList<String>();
        for (ConstraintViolation<?> violation : violations) {
            messages.add(violation.getPropertyPath() + "："
                    + safeValidationMessage(violation.getMessage()));
        }
        Collections.sort(messages);
        return String.join("；", messages);
    }

    private String safeValidationMessage(String message) {
        return message == null || message.trim().isEmpty()
                ? ErrorCode.REQUEST_PARAMETER_INVALID.getDefaultMessage() : message.trim();
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
