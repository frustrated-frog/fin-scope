package com.finscope.rpc.quant;

import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonQuantDailyBarSourceTest {

    @Test
    void parsesAdjustedBarsAndPreservesActualUpstreamProvenance() {
        AtomicReference<URI> requested = new AtomicReference<URI>();
        FinanceHttpClient http = (provider, uri, headers) -> {
            requested.set(uri);
            return response(payload("QFQ", "FRESH_FALLBACK"));
        };
        PythonQuantDailyBarSource source = new PythonQuantDailyBarSource(
                "http://127.0.0.1:8000/", http);

        QuantDailyBarBatch batch = source.fetch("600519.SH", 1000);

        assertEquals("/v1/stocks/SH/600519/daily-bars", requested.get().getPath());
        assertEquals("limit=1000", requested.get().getQuery());
        assertEquals("EASTMONEY_DIRECT", batch.getSourceCode());
        assertEquals("EASTMONEY", batch.getSourceFamily());
        assertEquals("FRESH_FALLBACK", batch.getQualityStatus());
        assertEquals(LocalDate.of(2026, 7, 16), batch.getAsOfDate());
        assertEquals(0, new BigDecimal("1480.50")
                .compareTo(batch.getBars().get(0).getAdjustedClose()));
        assertEquals("600519.SH", batch.getBars().get(0).getInstrumentCode());
        assertTrue(batch.isDegraded());
    }

    @Test
    void requestsUpToFiveThousandBarsForSingleStockResearch() {
        AtomicReference<URI> requested = new AtomicReference<URI>();
        PythonQuantDailyBarSource source = new PythonQuantDailyBarSource(
                "http://127.0.0.1:8000",
                (provider, uri, headers) -> {
                    requested.set(uri);
                    return response(payload("QFQ", "FRESH_PRIMARY"));
                });

        source.fetch("600519.SH", 5000);

        assertEquals("limit=5000", requested.get().getQuery());
    }

    @Test
    void rejectsUnadjustedBarsInsteadOfSilentlyMixingPriceSemantics() {
        PythonQuantDailyBarSource source = new PythonQuantDailyBarSource(
                "http://127.0.0.1:8000",
                (provider, uri, headers) -> response(payload("NONE", "FRESH_PRIMARY")));

        ProviderContractException error = assertThrows(
                ProviderContractException.class,
                () -> source.fetch("600519.SH", 1000));

        assertEquals("UNSUPPORTED_ADJUSTMENT", error.getErrorType());
        assertTrue(error.getMessage().contains("QFQ"));
    }

    @Test
    void rejectsUnavailableOrMismatchedResponses() {
        PythonQuantDailyBarSource unavailable = new PythonQuantDailyBarSource(
                "http://127.0.0.1:8000",
                (provider, uri, headers) -> response(payload("QFQ", "UNAVAILABLE")));
        PythonQuantDailyBarSource mismatched = new PythonQuantDailyBarSource(
                "http://127.0.0.1:8000",
                (provider, uri, headers) -> response(
                        payload("QFQ", "FRESH_PRIMARY").replace("600519", "000001")));

        assertEquals("UPSTREAM_UNAVAILABLE", assertThrows(
                ProviderContractException.class,
                () -> unavailable.fetch("600519.SH", 1000)).getErrorType());
        assertEquals("SYMBOL_MISMATCH", assertThrows(
                ProviderContractException.class,
                () -> mismatched.fetch("600519.SH", 1000)).getErrorType());
    }

    private static FinanceHttpResponse response(String body) {
        return new FinanceHttpResponse(200, body, Instant.parse("2026-07-16T07:00:01Z"), "hash");
    }

    private static String payload(String adjustment, String quality) {
        String data = "[\u007b"
                + "\"symbol\":\u007b\"market\":\"SH\",\"code\":\"600519\"\u007d,"
                + "\"trade_date\":\"2026-07-16\",\"open\":1475.00,"
                + "\"high\":1490.00,\"low\":1470.00,\"close\":1480.50,"
                + "\"volume\":1000,\"amount\":1480500,\"adjustment\":\"" + adjustment + "\"\u007d]";
        if ("UNAVAILABLE".equals(quality)) data = "null";
        return "\u007b"
                + "\"capability\":\"DAILY_BARS\","
                + "\"symbol\":\u007b\"market\":\"SH\",\"code\":\"600519\"\u007d,"
                + "\"quality_status\":\"" + quality + "\","
                + "\"source_code\":\"EASTMONEY_DIRECT\",\"source_family\":\"EASTMONEY\","
                + "\"as_of\":\"2026-07-16T15:00:00+08:00\","
                + "\"retrieved_at\":\"2026-07-16T07:00:01Z\","
                + "\"warnings\":[\"首选数据源不可用\"],\"attempts\":[],\"data\":" + data + "\u007d";
    }
}
