package com.finscope.rpc.marketintel.eastmoney;

import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.CapitalFlowData;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EastmoneyCapitalFlowProviderTest {
    @Test
    void parsesMinuteDailyQuoteAndAlignsTradeAmount() throws Exception {
        EastmoneyCapitalFlowProvider provider = new EastmoneyCapitalFlowProvider(new FixtureHttpClient());
        CapitalFlowData data = provider.fetch(stock(), LocalDate.of(2026, 7, 14));

        CapitalFlowPoint minute = data.getMinutePoints().get(0);
        assertEquals("MINUTE_1", minute.getGranularity());
        assertEquals(new BigDecimal("18000000"), minute.getMainNetInflow());
        assertEquals(new BigDecimal("120000000"), minute.getIntervalTradeAmount());
        assertEquals(new BigDecimal("81000"), minute.getTradeVolume());
        assertEquals(new BigDecimal("120000000"), minute.getCumulativeTradeAmount());
        assertEquals(new BigDecimal("1480.50"), minute.getPrice());
        assertNotEquals("eastmoney-fund-flow-minute.json", minute.getPayloadHash());
        CapitalFlowPoint secondMinute = data.getMinutePoints().get(1);
        assertEquals(new BigDecimal("4000000"), secondMinute.getMainNetInflow());
        assertEquals(new BigDecimal("30000000"), secondMinute.getIntervalTradeAmount());
        assertEquals(new BigDecimal("10000"), secondMinute.getTradeVolume());
        assertEquals(new BigDecimal("150000000"), secondMinute.getCumulativeTradeAmount());
        assertEquals(new BigDecimal("3.21"), secondMinute.getTurnoverRate());
        assertEquals(new BigDecimal("1.67"), secondMinute.getVolumeRatio());
        assertNull(minute.getMainInflow());
        assertNull(minute.getMainOutflow());
        assertEquals(new BigDecimal("3.21"), data.getTurnoverRate());
        assertEquals(new BigDecimal("1.67"), data.getVolumeRatio());
        assertEquals(2, data.getDailyPoints().size());
        CapitalFlowPoint latestDaily = data.getDailyPoints().get(1);
        assertEquals(new BigDecimal("1800000000"), latestDaily.getIntervalTradeAmount());
        assertEquals(new BigDecimal("1210000"), latestDaily.getTradeVolume());
        assertEquals(new BigDecimal("1481.50"), latestDaily.getPrice());
        assertNotEquals("eastmoney-fund-flow-daily.json", latestDaily.getPayloadHash());
    }

    @Test
    void routesRealtimeAndQuoteToPush2AndHistoryToPush2His() {
        RecordingFixtureHttpClient client = new RecordingFixtureHttpClient();
        EastmoneyCapitalFlowProvider provider = new EastmoneyCapitalFlowProvider(client);

        provider.fetch(stock(), LocalDate.of(2026, 7, 14));

        assertEquals("push2.eastmoney.com", client.find("/fflow/kline/get").getHost());
        assertEquals("push2his.eastmoney.com", client.find("/fflow/daykline/get").getHost());
        assertEquals("push2.eastmoney.com", client.find("/stock/get").getHost());
        assertTrue(client.find("/fflow/kline/get").getQuery().contains("lmt=500"));
        assertTrue(client.find("/fflow/daykline/get").getQuery().contains("lmt=20"));
        assertTrue(client.find("/stock/kline/get").getQuery().contains("end=20500000"));
        assertTrue(client.find("/stock/kline/get").getQuery().contains("fields1=f1,f2,f3,f4,f5,f6"));
    }

    @Test
    void keepsHistoricalFundFlowWhenAuxiliaryMarketSourcesFail() {
        FinanceHttpClient partiallyAvailable = (provider, uri, headers) -> {
            if (uri.getPath().contains("fflow/daykline")) return fixture(uri, "eastmoney-fund-flow-daily.json");
            throw new ProviderContractException("HTTP_503", "temporary unavailable", true);
        };

        CapitalFlowData data = new EastmoneyCapitalFlowProvider(partiallyAvailable)
                .fetch(stock(), LocalDate.of(2026, 7, 14));

        assertEquals(2, data.getDailyPoints().size());
        assertTrue(data.getMinutePoints().isEmpty());
        assertTrue(data.getWarnings().stream().anyMatch(value -> value.startsWith("REALTIME_FUND_FLOW_UNAVAILABLE")));
        assertTrue(data.getWarnings().stream().anyMatch(value -> value.startsWith("DAILY_MARKET_UNAVAILABLE")));
    }

    @Test
    void usesCurrentQuoteAsFallbackWhenDailyMarketEndpointFails() {
        FixtureHttpClient fixtures = new FixtureHttpClient();
        FinanceHttpClient missingDailyMarket = (provider, uri, headers) -> {
            if (uri.getPath().endsWith("/stock/kline/get")) {
                throw new ProviderContractException("HTTP_503", "daily market unavailable", true);
            }
            return fixtures.get(provider, uri, headers);
        };

        CapitalFlowData data = new EastmoneyCapitalFlowProvider(missingDailyMarket)
                .fetch(stock(), LocalDate.of(2026, 7, 14));

        CapitalFlowPoint latest = data.getDailyPoints().get(data.getDailyPoints().size() - 1);
        assertEquals(new BigDecimal("1200000000"), latest.getIntervalTradeAmount());
        assertEquals(new BigDecimal("810000"), latest.getTradeVolume());
        assertEquals(new BigDecimal("1480.50"), latest.getPrice());
        assertEquals(new BigDecimal("3.21"), latest.getTurnoverRate());
    }

    @Test
    void failsOnlyWhenBothFundFlowSourcesFail() {
        FinanceHttpClient unavailable = (provider, uri, headers) -> {
            throw new ProviderContractException("HTTP_503", "temporary unavailable", true);
        };
        EastmoneyCapitalFlowProvider provider = new EastmoneyCapitalFlowProvider(unavailable);

        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> provider.fetch(stock(), LocalDate.of(2026, 7, 14)));

        assertEquals("ALL_FUND_FLOW_SOURCES_FAILED", error.getErrorType());
        assertTrue(error.getMessage().contains("资金流接口"));
    }

    @Test
    void rejectsCoreKlineSchemaDrift() {
        FinanceHttpClient malformed = (provider, uri, headers) -> new FinanceHttpResponse(200,
                uri.getPath().contains("fflow/kline") ? "{\"data\":{\"klines\":[\"10:30,broken\"]}}" : "{\"data\":{}}",
                Instant.EPOCH, "hash");
        EastmoneyCapitalFlowProvider provider = new EastmoneyCapitalFlowProvider(malformed);
        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> provider.fetch(stock(), LocalDate.of(2026, 7, 14)));
        assertEquals("SCHEMA_DRIFT", error.getErrorType());
    }

    private static Instrument stock() {
        Instrument value = new Instrument(); value.setId(7L); value.setCode("600519");
        value.setMarket("SH"); value.setType("STOCK"); value.setName("贵州茅台"); return value;
    }

    private static class FixtureHttpClient implements FinanceHttpClient {
        @Override public FinanceHttpResponse get(String provider, URI uri, Map<String, String> headers) throws Exception {
            String path = uri.getPath(); String fixture;
            if (path.contains("fflow/daykline")) fixture = "eastmoney-fund-flow-daily.json";
            else if (path.contains("fflow/kline")) fixture = "eastmoney-fund-flow-minute.json";
            else if (path.contains("trends2")) fixture = "eastmoney-stock-trends.json";
            else if (path.contains("stock/kline")) fixture = "eastmoney-stock-daily.json";
            else fixture = "eastmoney-stock-quote.json";
            return fixture(uri, fixture);
        }
    }

    private static class RecordingFixtureHttpClient extends FixtureHttpClient {
        private final List<URI> requests = new ArrayList<URI>();
        @Override public FinanceHttpResponse get(String provider, URI uri, Map<String, String> headers) throws Exception {
            requests.add(uri);
            return super.get(provider, uri, headers);
        }
        URI find(String path) {
            return requests.stream().filter(uri -> uri.getPath().endsWith(path)).findFirst()
                    .orElseThrow(() -> new AssertionError("missing request: " + path));
        }
    }

    private static FinanceHttpResponse fixture(URI uri, String name) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(EastmoneyCapitalFlowProviderTest.class.getClassLoader()
                .getResource("marketintel/" + name).toURI()));
        return new FinanceHttpResponse(200, new String(bytes, StandardCharsets.UTF_8), Instant.EPOCH, name);
    }
}
