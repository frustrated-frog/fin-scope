package com.finscope.web.handler;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.config.RequestLoggingFilter;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestControllerAdvice(basePackages = "com.finscope.web.controller")
public class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> type = returnType.getParameterType();
        return !ApiResponse.class.isAssignableFrom(type)
                && !SseEmitter.class.isAssignableFrom(type)
                && !StreamingResponseBody.class.isAssignableFrom(type)
                && !Resource.class.isAssignableFrom(type)
                && !byte[].class.equals(type)
                && !String.class.equals(type);
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof ApiResponse
                || isNoContent(response)
                || !isJson(selectedContentType)) {
            return body;
        }
        return ApiResponse.success(body, currentTraceId());
    }

    private boolean isJson(MediaType mediaType) {
        return mediaType != null
                && (MediaType.APPLICATION_JSON.includes(mediaType)
                || mediaType.getSubtype().endsWith("+json"));
    }

    private boolean isNoContent(ServerHttpResponse response) {
        if (!(response instanceof ServletServerHttpResponse)) {
            return false;
        }
        return ((ServletServerHttpResponse) response).getServletResponse().getStatus()
                == HttpStatus.NO_CONTENT.value();
    }

    private String currentTraceId() {
        String traceId = MDC.get(RequestLoggingFilter.TRACE_ID);
        return traceId == null ? "" : traceId;
    }
}
