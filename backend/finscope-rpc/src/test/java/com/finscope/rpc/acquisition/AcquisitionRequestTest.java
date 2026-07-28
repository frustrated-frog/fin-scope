package com.finscope.rpc.acquisition;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AcquisitionRequestTest {

    @Test
    void createsSafeDefaultsForPublicGetRequest() {
        AcquisitionRequest request = AcquisitionRequest.get(URI.create("https://example.com/article"))
                .purpose("WEB_ARTICLE")
                .build();

        assertEquals("GET", request.getMethod());
        assertEquals(15000, request.getDeadlineMs());
        assertEquals(8 * 1024 * 1024, request.getMaxResponseBytes());
        assertEquals(1, request.getMaxRetries());
    }

    @Test
    void removesSensitiveHeadersFromAuditView() {
        AcquisitionRequest request = AcquisitionRequest.get(URI.create("https://example.com/article"))
                .header("Accept", "text/html")
                .header("Cookie", "session=secret")
                .header("Authorization", "Bearer secret")
                .header("X-Api-Key", "secret")
                .build();

        Map<String, String> headers = request.auditHeaders();

        assertEquals("text/html", headers.get("Accept"));
        assertFalse(headers.containsKey("Cookie"));
        assertFalse(headers.containsKey("Authorization"));
        assertFalse(headers.containsKey("X-Api-Key"));
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertThrows(IllegalArgumentException.class,
                () -> AcquisitionRequest.get(URI.create("file:///tmp/private.txt")).build());
    }
}
