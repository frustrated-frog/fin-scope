package com.finscope.rpc.marketpulse;

import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PythonResearchMemberSourceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 11);
    private static final String PAYLOAD = """
            {"schema_version":"research-member-v1","business_date":"2026-09-11",
             "instrument_code":"600519.SH","status":"READY","reason":"COMPLETE",
             "message":"22日行情完整","valid_bars":22,"required_bars":22,"source_code":"LOCAL"}
            """;

    @Test
    void mapsPostAndCoverageRatherThanTreatingHttpSuccessAsReady() {
        var result = source(PAYLOAD).ensure(DATE, "600519.SH");
        assertEquals(22, result.getValidBars());
        assertEquals("READY", result.getStatus().name());
        var partial = source(PAYLOAD.replace("READY", "PARTIAL").replace("COMPLETE", "HISTORY_GAP")
                .replace("\"valid_bars\":22", "\"valid_bars\":12")).ensure(DATE, "600519.SH");
        assertEquals("PARTIAL", partial.getStatus().name());
        assertEquals(12, partial.getValidBars());
    }

    @Test
    void rejectsMismatchedIdentityAndImpossibleReadyCoverage() {
        for (String payload : new String[] {
                PAYLOAD.replace("2026-09-11", "2026-09-10"),
                PAYLOAD.replace("600519.SH", "600000.SH"),
                PAYLOAD.replace("research-member-v1", "research-member-v2"),
                PAYLOAD.replace("\"valid_bars\":22", "\"valid_bars\":21"),
                PAYLOAD.replace("COMPLETE", "NO_DATA"),
                PAYLOAD.replace("\"valid_bars\":22", "\"valid_bars\":-1")
        }) {
            assertThrows(ProviderContractException.class, () -> source(payload).ensure(DATE, "600519.SH"));
        }
    }

    private PythonResearchMemberSource source(String payload) {
        var source = new PythonResearchMemberSource();
        ReflectionTestUtils.setField(source, "baseUrl", "http://localhost:8000/");
        FinanceHttpClient http = new FinanceHttpClient() {
            @Override
            public FinanceHttpResponse get(String provider, URI uri, Map<String, String> headers) {
                throw new AssertionError("补齐须使用POST");
            }
            @Override
            public FinanceHttpResponse postJson(String provider, URI uri, String body,
                    Map<String, String> headers, int timeoutMs, int maxBytes) {
                assertEquals("/v1/markets/CN-A/daily-research/members/600519.SH", uri.getPath());
                assertEquals("business_date=2026-09-11", uri.getQuery());
                assertTrue(timeoutMs >= 60000);
                assertTrue(maxBytes <= 65536);
                return new FinanceHttpResponse(200, payload, Instant.now(), "test");
            }
        };
        ReflectionTestUtils.setField(source, "http", http);
        return source;
    }
}
