package com.finscope.web.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String TRACE_ID = "traceId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final String SAFE_REQUEST_ID_PATTERN = "[A-Za-z0-9._:-]+";

    @Value("${finscope.logging.slow-request-ms:2000}")
    private long slowRequestMs = 2000L;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String traceId = resolveTraceId(request);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(REQUEST_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            logCompletion(request, response, durationMs);
            MDC.remove(TRACE_ID);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming != null) {
            String candidate = incoming.trim();
            if (!candidate.isEmpty()
                    && candidate.length() <= MAX_REQUEST_ID_LENGTH
                    && candidate.matches(SAFE_REQUEST_ID_PATTERN)) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString();
    }

    private void logCompletion(HttpServletRequest request,
                               HttpServletResponse response,
                               long durationMs) {
        Object[] arguments = new Object[]{
                LogSanitizer.clean(request.getMethod(), 16),
                LogSanitizer.clean(request.getRequestURI(), 512),
                LogSanitizer.clean(request.getQueryString(), 512),
                response.getStatus(),
                durationMs,
                LogSanitizer.clean(request.getRemoteAddr(), 128)
        };
        if (response.getStatus() >= 400 || durationMs >= slowRequestMs) {
            log.warn("请求完成 method={} path={} query={} status={} durationMs={} remote={}", arguments);
        } else {
            log.info("请求完成 method={} path={} query={} status={} durationMs={} remote={}", arguments);
        }
    }
}
