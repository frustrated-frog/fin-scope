package com.finscope.rpc.quote;

import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PythonTradingCalendarClientTest {
    @Test
    void requestsPreviousSessionAndMapsDate() {
        PythonTradingCalendarClient client = client((provider, uri, headers) -> {
            assertEquals("/v1/calendar/previous-session", uri.getPath());
            assertEquals("before=2026-10-08", uri.getQuery());
            return new FinanceHttpResponse(200, "{\"previous_session\":\"2026-09-30\"}", Instant.now(), "hash");
        });
        assertEquals(LocalDate.parse("2026-09-30"), client.previousSession(LocalDate.parse("2026-10-08")));
    }

    @Test
    void rejectsUnknownCalendarAndInvalidOrFutureDates() {
        for (String body : new String[]{"{}", "{\"previous_session\":\"2026-10-08\"}",
                "{\"previous_session\":\"2026-10-09\"}"}) {
            PythonTradingCalendarClient client = client((provider, uri, headers) ->
                    new FinanceHttpResponse(200, body, Instant.now(), "hash"));
            assertThrows(ProviderContractException.class, () -> client.previousSession(LocalDate.parse("2026-10-08")));
        }
        PythonTradingCalendarClient unavailable = client((provider, uri, headers) ->
                new FinanceHttpResponse(503, "{}", Instant.now(), "hash"));
        assertThrows(ProviderContractException.class, () -> unavailable.previousSession(LocalDate.parse("2026-10-08")));
    }

    private PythonTradingCalendarClient client(FinanceHttpClient http) {
        PythonTradingCalendarClient client = new PythonTradingCalendarClient();
        ReflectionTestUtils.setField(client, "http", http);
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:8000/");
        return client;
    }
}
