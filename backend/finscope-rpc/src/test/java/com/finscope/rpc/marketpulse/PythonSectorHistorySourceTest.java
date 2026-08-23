package com.finscope.rpc.marketpulse;

import com.finscope.domain.marketpulse.SectorHistorySnapshot;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PythonSectorHistorySourceTest {

    @Test
    void parsesVersionedFullIndustryHistory() {
        AtomicReference<URI> requested = new AtomicReference<URI>();
        FinanceHttpClient http = (provider, uri, headers) -> {
            requested.set(uri);
            return response(payload("sector-history-v1", "2026-08-21"));
        };
        PythonSectorHistorySource source = source(http);

        SectorHistorySnapshot result = source.fetch(LocalDate.of(2026, 8, 21), 60);

        assertEquals("/v1/sectors/INDUSTRY/history", requested.get().getPath());
        assertEquals("business_date=2026-08-21&window=60", requested.get().getQuery());
        assertEquals("TONGHUASHUN", result.getSourceFamily());
        assertEquals(2, result.getEntries().size());
        assertEquals(6.5D, result.getEntries().get(0).getReturn20d());
        assertEquals(4, result.getEntries().get(0).getPositiveDays5());
    }

    @Test
    void rejectsSchemaAndDateDrift() {
        PythonSectorHistorySource schema = source((provider, uri, headers) ->
                response(payload("sector-history-v2", "2026-08-21")));
        PythonSectorHistorySource date = source((provider, uri, headers) ->
                response(payload("sector-history-v1", "2026-08-20")));

        assertEquals("SECTOR_HISTORY_SCHEMA_DRIFT", assertThrows(
                ProviderContractException.class,
                () -> schema.fetch(LocalDate.of(2026, 8, 21), 60)).getErrorType());
        assertEquals("SECTOR_HISTORY_DATE_MISMATCH", assertThrows(
                ProviderContractException.class,
                () -> date.fetch(LocalDate.of(2026, 8, 21), 60)).getErrorType());
    }

    @Test
    void rejectsSourceAndStaleIndustryDrift() {
        PythonSectorHistorySource sourceCode = source((provider, uri, headers) -> response(
                payload("sector-history-v1", "2026-08-21")
                        .replace("AKSHARE_TONGHUASHUN_SECTOR_HISTORY", "UNKNOWN_SOURCE")));
        PythonSectorHistorySource stale = source((provider, uri, headers) -> response(
                payload("sector-history-v1", "2026-08-21")
                        .replace("\"last_trade_date\":\"2026-08-21\"",
                                "\"last_trade_date\":\"2026-08-20\"")));

        assertEquals("SECTOR_HISTORY_SOURCE_DRIFT", assertThrows(
                ProviderContractException.class,
                () -> sourceCode.fetch(LocalDate.of(2026, 8, 21), 60)).getErrorType());
        assertEquals("SECTOR_HISTORY_STALE_DATA", assertThrows(
                ProviderContractException.class,
                () -> stale.fetch(LocalDate.of(2026, 8, 21), 60)).getErrorType());
    }

    private PythonSectorHistorySource source(FinanceHttpClient http) {
        PythonSectorHistorySource value = new PythonSectorHistorySource();
        ReflectionTestUtils.setField(value, "baseUrl", "http://127.0.0.1:8000/");
        ReflectionTestUtils.setField(value, "http", http);
        return value;
    }

    private FinanceHttpResponse response(String body) {
        return new FinanceHttpResponse(200, body,
                Instant.parse("2026-08-23T10:00:00Z"), "hash");
    }

    private String payload(String schema, String date) {
        return "{\"schema_version\":\"" + schema + "\","
                + "\"source_code\":\"AKSHARE_TONGHUASHUN_SECTOR_HISTORY\","
                + "\"source_family\":\"TONGHUASHUN\",\"category\":\"INDUSTRY\","
                + "\"business_date\":\"" + date + "\",\"quality_status\":\"FRESH_PRIMARY\","
                + "\"retrieved_at\":\"2026-08-23T18:00:00\",\"requested_window\":60,"
                + "\"covered_trade_dates\":[\"2026-08-20\",\"2026-08-21\"],"
                + "\"entries\":["
                + "{\"code\":\"881121\",\"name\":\"半导体\",\"last_trade_date\":\"2026-08-21\","
                + "\"coverage_days\":60,\"return_1d\":0.8,\"return_5d\":3.2,"
                + "\"return_20d\":6.5,\"positive_days_5\":4},"
                + "{\"code\":\"881273\",\"name\":\"白酒\",\"last_trade_date\":\"2026-08-21\","
                + "\"coverage_days\":60,\"return_1d\":-1.1,\"return_5d\":-2.4,"
                + "\"return_20d\":-4.5,\"positive_days_5\":1}],\"warnings\":[]}";
    }
}
