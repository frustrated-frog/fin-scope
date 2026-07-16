package com.finscope.web.response;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.config.RequestLoggingFilter;
import org.slf4j.MDC;

public final class ApiResponses {

    private ApiResponses() {
    }

    public static <T> ApiResponse<T> success(T data) {
        String traceId = MDC.get(RequestLoggingFilter.TRACE_ID);
        return ApiResponse.success(data, traceId == null ? "" : traceId);
    }
}
