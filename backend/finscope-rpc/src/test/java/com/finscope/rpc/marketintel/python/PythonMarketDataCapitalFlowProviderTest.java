package com.finscope.rpc.marketintel.python;

import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.CapitalFlowData;
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

class PythonMarketDataCapitalFlowProviderTest {
    @Test
    void parsesNormalizedCapitalFlowAndPreservesUnderlyingSource() {
        AtomicReference<URI> requested = new AtomicReference<URI>();
        FinanceHttpClient http = (provider, uri, headers) -> {
            requested.set(uri);
            return response(freshPayload());
        };
        PythonMarketDataCapitalFlowProvider provider =
                new PythonMarketDataCapitalFlowProvider("http://127.0.0.1:8000/", http);

        CapitalFlowData data = provider.fetch(stock(), LocalDate.of(2026, 7, 16));

        assertEquals("/v1/stocks/SH/600519/capital-flow", requested.get().getPath());
        assertEquals("require_minute=true", requested.get().getQuery());
        assertEquals("PYTHON_MARKET_DATA", data.getProviderCode());
        assertEquals(new BigDecimal("1.53"), data.getTurnoverRate());
        assertEquals(1, data.getMinutePoints().size());
        assertEquals(1, data.getDailyPoints().size());
        CapitalFlowPoint minute = data.getMinutePoints().get(0);
        assertEquals(new BigDecimal("1000000.0"), minute.getMainNetInflow());
        assertEquals(new BigDecimal("11.23"), minute.getPrice());
        assertEquals("COMPLETE", minute.getQualityStatus());
        assertTrue(data.getWarnings().contains("source:EASTMONEY_DIRECT"));
    }

    @Test
    void rejectsSnapshotFallbackSoJavaCanTryAnotherOnlineProvider() {
        FinanceHttpClient http = (provider, uri, headers) -> response(
                freshPayload().replace("FRESH_PRIMARY", "STALE_FALLBACK"));
        PythonMarketDataCapitalFlowProvider provider =
                new PythonMarketDataCapitalFlowProvider("http://python-market-data:8000", http);

        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> provider.fetch(stock(), LocalDate.of(2026, 7, 16)));

        assertEquals("PYTHON_STALE_FALLBACK", error.getErrorType());
    }

    @Test
    void providerAlwaysSupportsAshareStocksWithoutEnableSwitch() {
        PythonMarketDataCapitalFlowProvider provider = new PythonMarketDataCapitalFlowProvider(
                "http://127.0.0.1:8000",
                (providerCode, uri, headers) -> response(freshPayload()));

        assertTrue(provider.supports(stock()));
    }

    private static Instrument stock() {
        Instrument instrument = new Instrument();
        instrument.setId(7L);
        instrument.setType("STOCK");
        instrument.setMarket("SH");
        instrument.setCode("600519");
        instrument.setName("贵州茅台");
        return instrument;
    }

    private static FinanceHttpResponse response(String body) {
        return new FinanceHttpResponse(200, body, Instant.parse("2026-07-16T02:30:01Z"), "payload-hash");
    }

    private static String freshPayload() {
        return "{"
                + "\"capability\":\"CAPITAL_FLOW\","
                + "\"symbol\":{\"market\":\"SH\",\"code\":\"600519\"},"
                + "\"quality_status\":\"FRESH_PRIMARY\","
                + "\"source_code\":\"EASTMONEY_DIRECT\","
                + "\"source_family\":\"EASTMONEY\","
                + "\"as_of\":\"2026-07-16T10:30:00+08:00\","
                + "\"retrieved_at\":\"2026-07-16T02:30:01Z\","
                + "\"warnings\":[],\"attempts\":[],"
                + "\"data\":{"
                + "\"turnover_rate\":1.53,\"volume_ratio\":1.21,\"warnings\":[],"
                + "\"minute_points\":[{"
                + "\"symbol\":{\"market\":\"SH\",\"code\":\"600519\"},"
                + "\"granularity\":\"MINUTE_1\",\"observed_at\":\"2026-07-16T10:30:00+08:00\","
                + "\"price\":11.23,\"main_net_inflow\":1000000.0,"
                + "\"super_large_net_inflow\":500000.0,\"large_net_inflow\":500000.0,"
                + "\"medium_net_inflow\":-200000.0,\"small_net_inflow\":-800000.0,"
                + "\"volume\":12345600.0,\"amount\":138000000.0,"
                + "\"turnover_rate\":1.53,\"volume_ratio\":1.21,\"quality_status\":\"COMPLETE\"}],"
                + "\"daily_points\":[{"
                + "\"symbol\":{\"market\":\"SH\",\"code\":\"600519\"},"
                + "\"granularity\":\"DAY_1\",\"observed_at\":\"2026-07-16T15:00:00+08:00\","
                + "\"price\":11.23,\"main_net_inflow\":800000.0,\"quality_status\":\"COMPLETE\"}]"
                + "}}";
    }
}
