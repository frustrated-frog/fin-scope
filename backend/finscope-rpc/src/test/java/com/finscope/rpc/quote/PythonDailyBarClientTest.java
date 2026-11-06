package com.finscope.rpc.quote;

import com.finscope.domain.instrument.DailyBarPoint;
import com.finscope.domain.instrument.DailyBarHistory;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonDailyBarClientTest {

    @Test
    void benchmarkUsesShanghaiIndexIdentityRatherThanStockCodeInference() {
        FinanceHttpClient http = (provider, uri, headers) -> {
            assertEquals("/v1/stocks/SH/000300/daily-bars", uri.getPath());
            return new FinanceHttpResponse(200, "{\"quality_status\":\"FRESH_PRIMARY\",\"data\":[{"
                    + "\"trade_date\":\"2026-09-18\",\"open\":100,\"high\":104,\"low\":99,\"close\":103,\"volume\":100}]}",
                    Instant.now(), "hash");
        };
        List<DailyBarPoint> bars = client("http://localhost:8000", http).fetchMarketBenchmark(250);
        assertEquals("SH", bars.get(0).getMarket());
        assertEquals("000300", bars.get(0).getCode());
    }

    @Test
    void mapsDailyBarsAndInfersShanghaiMarketFromCode() throws Exception {
        AtomicReference<URI> requested = new AtomicReference<URI>();
        FinanceHttpClient http = (providerCode, uri, headers) -> {
            requested.set(uri);
            String body = "{\"quality_status\":\"FRESH_PRIMARY\",\"source_code\":\"PYTDX\","
                    + "\"source_family\":\"TDX\",\"data\":["
                    + "{\"symbol\":{\"code\":\"600519\",\"market\":\"SH\"},\"trade_date\":\"2026-07-31\","
                    + "\"open\":1300.0,\"high\":1310.0,\"low\":1295.0,\"close\":1305.5,"
                    + "\"volume\":50000,\"amount\":6500000000,\"amplitude\":1.15,"
                    + "\"change_pct\":0.8,\"turnover_rate\":0.3},"
                    + "{\"symbol\":{\"code\":\"600519\",\"market\":\"SH\"},\"trade_date\":\"2026-08-03\","
                    + "\"open\":1305.0,\"high\":1320.0,\"low\":1300.0,\"close\":1318.0,"
                    + "\"volume\":52000,\"amount\":6800000000,\"amplitude\":1.2,"
                    + "\"change_pct\":0.96,\"turnover_rate\":0.31}"
                    + "]}";
            return new FinanceHttpResponse(200, body, Instant.parse("2026-08-03T07:00:00Z"), "hash");
        };
        PythonDailyBarClient client = client(
                "http://python-market-data:8000", http);

        List<DailyBarPoint> bars = client.fetchDailyBars("600519", 120);

        assertEquals("/v1/stocks/SH/600519/daily-bars", requested.get().getPath());
        assertEquals("limit=120", requested.get().getQuery());
        assertEquals(2, bars.size());
        DailyBarPoint latest = bars.get(1);
        assertEquals("600519", latest.getCode());
        assertEquals("SH", latest.getMarket());
        assertEquals("2026-08-03", latest.getTradeDate().toString());
        assertEquals(1305.0, latest.getOpen().doubleValue());
        assertEquals(1320.0, latest.getHigh().doubleValue());
        assertEquals(1300.0, latest.getLow().doubleValue());
        assertEquals(1318.0, latest.getClose().doubleValue());
        assertEquals(0.96, latest.getChangePct().doubleValue());
    }

    @Test
    void infersShenzhenAndBeijingMarketsFromCode() throws Exception {
        FinanceHttpClient http = (providerCode, uri, headers) -> {
            String market = uri.getPath().split("/")[3];
            String body = market.equals("BJ")
                    ? "{\"quality_status\":\"COMPLETE\",\"data\":[{\"symbol\":{\"code\":\"830799\",\"market\":\"BJ\"},"
                    + "\"trade_date\":\"2026-08-03\",\"open\":1.0,\"high\":1.0,\"low\":1.0,\"close\":1.0,"
                    + "\"volume\":1}]}"
                    : "{\"quality_status\":\"COMPLETE\",\"data\":[{\"symbol\":{\"code\":\"000001\",\"market\":\"SZ\"},"
                    + "\"trade_date\":\"2026-08-03\",\"open\":1.0,\"high\":1.0,\"low\":1.0,\"close\":1.0,"
                    + "\"volume\":1}]}";
            return new FinanceHttpResponse(200, body, Instant.now(), "hash");
        };
        PythonDailyBarClient client = client("http://localhost:8000", http);

        assertEquals("SZ", client.fetchDailyBars("000001", 30).get(0).getMarket());
        assertEquals("BJ", client.fetchDailyBars("830799", 30).get(0).getMarket());
    }

    @Test
    void forwardsExplicitRefreshToPythonService() throws Exception {
        AtomicReference<URI> requested = new AtomicReference<URI>();
        FinanceHttpClient http = (providerCode, uri, headers) -> {
            requested.set(uri);
            return new FinanceHttpResponse(200,
                    "{\"quality_status\":\"COMPLETE\",\"data\":[{\"trade_date\":\"2026-08-03\","
                            + "\"open\":1,\"high\":1,\"low\":1,\"close\":1,\"volume\":1}]}",
                    Instant.now(), "hash");
        };
        PythonDailyBarClient client = client("http://localhost:8000", http);

        client.fetchDailyBars("600519", 120, true);

        assertEquals("limit=120&refresh=true", requested.get().getQuery());
    }

    @Test
    void unavailableUpstreamThrowsRetryableContractError() throws Exception {
        FinanceHttpClient http = (providerCode, uri, headers) -> new FinanceHttpResponse(200,
                "{\"quality_status\":\"UNAVAILABLE\",\"data\":null}", Instant.now(), "hash");
        PythonDailyBarClient client = client("http://localhost:8000", http);

        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> client.fetchDailyBars("600519", 60));
        assertEquals("UPSTREAM_UNAVAILABLE", error.getErrorType());
        assertTrue(error.isRetryable());
    }

    @Test
    void historyPreservesProvenanceExcludesIncompleteAndUsesLongerWindow() {
        FinanceHttpClient http = (provider, uri, headers) -> {
            assertEquals("limit=500&include_current=false&refresh=true", uri.getQuery());
            return response("{\"source_code\":\"TENCENT_DAILY\",\"quality_status\":\"STALE_FALLBACK\","
                    + "\"as_of\":\"2026-01-05T15:15:00+08:00\",\"retrieved_at\":\"2026-01-06T08:00:00+08:00\","
                    + "\"warnings\":[\"旧快照\"],\"data\":["
                    + row("2026-01-05", true) + "," + row("2026-01-06", false) + "]}");
        };
        DailyBarHistory history = client("http://localhost:8000", http).fetchHistory("600519", "SH", 999, true);
        assertEquals(1, history.getBars().size());
        assertEquals("QFQ", history.getBars().get(0).getAdjustment());
        assertEquals(Boolean.TRUE, history.getBars().get(0).getIsComplete());
        assertEquals("TENCENT_DAILY", history.getSourceCode());
        assertEquals("STALE_FALLBACK", history.getQualityStatus());
        assertEquals("2026-01-05T15:15:00+08:00", history.getAsOf());
        assertEquals("旧快照", history.getWarnings().get(0));
    }

    @Test
    void historyRejectsDuplicateAndIncoherentPricesRatherThanCalculatingAcrossThem() {
        FinanceHttpClient duplicate = (provider, uri, headers) -> response("{\"data\":["
                + row("2026-01-05", true) + "," + row("2026-01-05", true) + "]}");
        assertThrows(ProviderContractException.class,
                () -> client("http://localhost:8000", duplicate).fetchHistory("600519", "SH", 500, false));
        FinanceHttpClient invalid = (provider, uri, headers) -> response("{\"data\":["
                + row("2026-01-05", true).replace("\"close\":100", "\"close\":200") + "]}");
        assertThrows(ProviderContractException.class,
                () -> client("http://localhost:8000", invalid).fetchHistory("600519", "SH", 500, false));
    }

    @Test
    void historyRejectsInvalidSymbolsBeforeRequest() {
        FinanceHttpClient http = (provider, uri, headers) -> {
            throw new AssertionError("invalid symbol must not reach network");
        };
        assertThrows(ProviderContractException.class,
                () -> client("http://localhost:8000", http).fetchHistory("../600519", "SH", 500, false));
        assertThrows(ProviderContractException.class,
                () -> client("http://localhost:8000", http).fetchHistory("600519", "US", 500, false));
    }

    private static String row(String date, boolean complete) {
        return "{\"trade_date\":\"" + date + "\",\"open\":100,\"high\":101,\"low\":99,"
                + "\"close\":100,\"volume\":100,\"adjustment\":\"QFQ\",\"is_complete\":" + complete + "}";
    }

    private static FinanceHttpResponse response(String body) {
        return new FinanceHttpResponse(200, body, Instant.now(), "hash");
    }

    private static PythonDailyBarClient client(String baseUrl, FinanceHttpClient http) {
        PythonDailyBarClient client = new PythonDailyBarClient();
        ReflectionTestUtils.setField(client, "baseUrl", baseUrl);
        ReflectionTestUtils.setField(client, "http", http);
        return client;
    }
}
