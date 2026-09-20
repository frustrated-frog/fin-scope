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

    @Test
    void mapsEventWindowAndRejectsMisalignedOrUnorderedSessions() {
        String body = "{\"sessions\":[\"2026-09-10\",\"2026-09-11\",\"2026-09-14\",\"2026-09-15\","
                + "\"2026-09-16\",\"2026-09-17\",\"2026-09-18\",\"2026-09-21\",\"2026-09-22\",\"2026-09-23\",\"2026-09-24\"]}";
        PythonTradingCalendarClient valid = client((provider, uri, headers) -> {
            assertEquals("on_or_after=2026-09-18", uri.getQuery());
            return new FinanceHttpResponse(200, body, Instant.now(), "hash");
        });
        assertEquals(LocalDate.parse("2026-09-17"), valid.eventWindow(LocalDate.parse("2026-09-18")).get(5));
        for (String invalid : new String[]{"{}", body.replace("2026-09-18", "2026-09-17")}) {
            PythonTradingCalendarClient broken = client((provider, uri, headers) ->
                    new FinanceHttpResponse(200, invalid, Instant.now(), "hash"));
            assertThrows(ProviderContractException.class, () -> broken.eventWindow(LocalDate.parse("2026-09-18")));
        }
    }

    private PythonTradingCalendarClient client(FinanceHttpClient http) {
        PythonTradingCalendarClient client = new PythonTradingCalendarClient();
        ReflectionTestUtils.setField(client, "http", http);
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:8000/");
        return client;
    }
}
