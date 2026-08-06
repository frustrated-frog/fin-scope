package com.finscope.rpc.quote;

import com.finscope.domain.instrument.DailyBarPoint;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonDailyBarClientTest {

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
        PythonDailyBarClient client = new PythonDailyBarClient(
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
        PythonDailyBarClient client = new PythonDailyBarClient("http://localhost:8000", http);

        assertEquals("SZ", client.fetchDailyBars("000001", 30).get(0).getMarket());
        assertEquals("BJ", client.fetchDailyBars("830799", 30).get(0).getMarket());
    }

    @Test
    void unavailableUpstreamThrowsRetryableContractError() throws Exception {
        FinanceHttpClient http = (providerCode, uri, headers) -> new FinanceHttpResponse(200,
                "{\"quality_status\":\"UNAVAILABLE\",\"data\":null}", Instant.now(), "hash");
        PythonDailyBarClient client = new PythonDailyBarClient("http://localhost:8000", http);

        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> client.fetchDailyBars("600519", 60));
        assertEquals("UPSTREAM_UNAVAILABLE", error.getErrorType());
        assertTrue(error.isRetryable());
    }
}
