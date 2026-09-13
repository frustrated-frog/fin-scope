package com.finscope.rpc.desktopths;

import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.net.URI;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ThsDesktopClientTest {
    private ThsDesktopClient client(FinanceHttpClient http) {
        ThsDesktopClient client = new ThsDesktopClient();
        ReflectionTestUtils.setField(client, "http", http);
        ReflectionTestUtils.setField(client, "baseUrl", "http://127.0.0.1:18765");
        return client;
    }

    @Test
    void preservesPartialSnapshotAndRequestBoundary() throws Exception {
        FinanceHttpClient http = mock(FinanceHttpClient.class);
        when(http.postJson(anyString(), any(), anyString(), anyMap(), anyInt(), anyInt()))
            .thenReturn(new FinanceHttpResponse(200, "{\"status\":\"PARTIAL\",\"source\":\"THS_DESKTOP_AX\",\"capturedAt\":\"2026-09-13T03:00:00Z\",\"stockCode\":\"605069\",\"warnings\":[\"日期未获取\"]}", Instant.now(), ""));
        var result = client(http).capture();
        assertEquals("605069", result.getStockCode());
        assertNull(result.getDataDate());
        assertEquals("PARTIAL", result.getStatus().name());
        verify(http).postJson(eq("THS_DESKTOP"), eq(URI.create("http://127.0.0.1:18765/v1/capture")), eq("{}"),
            argThat(headers -> "1".equals(headers.get("X-FinScope-Desktop"))), eq(18000), eq(262144));
    }

    @Test
    void invalidContractAndNetworkFailureNeverBecomeSuccess() throws Exception {
        FinanceHttpClient http = mock(FinanceHttpClient.class);
        when(http.postJson(anyString(), any(), anyString(), anyMap(), anyInt(), anyInt()))
            .thenReturn(new FinanceHttpResponse(200, "{}", Instant.now(), ""))
            .thenThrow(new java.net.ConnectException("private diagnostic"));
        assertEquals("READ_FAILED", client(http).capture().getStatus().name());
        var failed = client(http).capture();
        assertEquals("BRIDGE_UNAVAILABLE", failed.getStatus().name());
        assertFalse(failed.getMessage().contains("private diagnostic"));
    }
}
