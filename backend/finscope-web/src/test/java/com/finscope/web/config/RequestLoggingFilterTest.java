package com.finscope.web.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestLoggingFilterTest {

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void reusesSafeIncomingRequestIdAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "trace-123_ABC");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestLoggingFilter().doFilter(request, response, new MockFilterChain());

        assertEquals("trace-123_ABC", response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER));
        assertNull(MDC.get(RequestLoggingFilter.TRACE_ID));
    }

    @Test
    void replacesUnsafeOrOversizedRequestIds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER,
                "unsafe\n" + repeat("x", 200));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestLoggingFilter().doFilter(request, response, new MockFilterChain());

        String traceId = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertTrue(traceId != null && traceId.length() == UUID.randomUUID().toString().length());
        UUID.fromString(traceId);
    }

    @Test
    void sanitizesLogValues() {
        assertEquals("a b", LogSanitizer.clean("a\nb", 20));
        assertEquals("12345...", LogSanitizer.clean("123456789", 5));
        assertEquals("-", LogSanitizer.clean(null, 20));
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
