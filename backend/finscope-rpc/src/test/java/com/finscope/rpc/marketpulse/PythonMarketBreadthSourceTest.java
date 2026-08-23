package com.finscope.rpc.marketpulse;

import com.finscope.domain.marketpulse.MarketBreadthSnapshot;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonMarketBreadthSourceTest {

    @Test
    void parsesVersionedMarketBreadthAndPreservesProvenance() {
        AtomicReference<URI> requested = new AtomicReference<URI>();
        FinanceHttpClient http = (provider, uri, headers) -> {
            requested.set(uri);
            return response(payload("FRESH_FALLBACK", "2026-08-21"));
        };
        PythonMarketBreadthSource source = new PythonMarketBreadthSource(
                "http://127.0.0.1:8000/", http);

        MarketBreadthSnapshot result = source.fetch(LocalDate.of(2026, 8, 21));

        assertEquals("/v1/markets/CN-A/breadth", requested.get().getPath());
        assertEquals("business_date=2026-08-21", requested.get().getQuery());
        assertEquals(LocalDate.of(2026, 8, 21), result.getBusinessDate());
        assertEquals("AKSHARE_SINA_A_SPOT", result.getSourceCode());
        assertEquals("SINA", result.getSourceFamily());
        assertEquals("FRESH_FALLBACK", result.getQualityStatus());
        assertEquals(3200, result.getAdvanceCount());
        assertEquals(1800, result.getDeclineCount());
        assertEquals(100, result.getFlatCount());
        assertEquals(5100, result.getValidCount());
        assertEquals(3200D / 5100D, result.getAdvanceRatio());
        assertEquals(2_300_000_000_000D, result.getTotalAmount());
        assertEquals(68, result.getLimitUpCount());
        assertEquals(4, result.getLimitDownCount());
        assertEquals(0.7D, result.getMedianChangePct());
        assertTrue(result.getWarnings().contains("东方财富不可用"));
    }

    @Test
    void rejectsSchemaDriftAndBusinessDateMismatch() {
        PythonMarketBreadthSource schemaDrift = source(
                payload("FRESH_PRIMARY", "2026-08-21")
                        .replace("market-breadth-v1", "market-breadth-v2"));
        PythonMarketBreadthSource dateMismatch = source(
                payload("FRESH_PRIMARY", "2026-08-20"));

        assertEquals("MARKET_BREADTH_SCHEMA_DRIFT", assertThrows(
                ProviderContractException.class,
                () -> schemaDrift.fetch(LocalDate.of(2026, 8, 21))).getErrorType());
        assertEquals("MARKET_BREADTH_DATE_MISMATCH", assertThrows(
                ProviderContractException.class,
                () -> dateMismatch.fetch(LocalDate.of(2026, 8, 21))).getErrorType());
    }

    private PythonMarketBreadthSource source(String body) {
        return new PythonMarketBreadthSource(
                "http://127.0.0.1:8000",
                (provider, uri, headers) -> response(body));
    }

    private static FinanceHttpResponse response(String body) {
        return new FinanceHttpResponse(200, body,
                Instant.parse("2026-08-21T07:20:00Z"), "hash");
    }

    private static String payload(String quality, String date) {
        return "{\"schema_version\":\"market-breadth-v1\",\"market\":\"CN-A\","
                + "\"business_date\":\"" + date + "\","
                + "\"source_code\":\"AKSHARE_SINA_A_SPOT\",\"source_family\":\"SINA\","
                + "\"quality_status\":\"" + quality + "\","
                + "\"retrieved_at\":\"2026-08-21T15:20:00+08:00\","
                + "\"advance_count\":3200,\"decline_count\":1800,\"flat_count\":100,"
                + "\"valid_count\":5100,\"advance_ratio\":0.6274509803921569,"
                + "\"total_amount\":2300000000000,\"limit_up_count\":68,"
                + "\"limit_down_count\":4,\"median_change_pct\":0.7,"
                + "\"warnings\":[\"东方财富不可用\"]}";
    }
}
