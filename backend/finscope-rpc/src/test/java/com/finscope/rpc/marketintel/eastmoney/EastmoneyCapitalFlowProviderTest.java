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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EastmoneyCapitalFlowProviderTest {
    @Test
    void parsesMinuteDailyQuoteAndAlignsTradeAmount() throws Exception {
        EastmoneyCapitalFlowProvider provider = new EastmoneyCapitalFlowProvider(new FixtureHttpClient(),
                Clock.fixed(Instant.parse("2026-07-14T02:32:00Z"), ZoneOffset.UTC));
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
        assertEquals(new BigDecimal("30000000"), secondMinute.getIntervalTradeAmount());
        assertEquals(new BigDecimal("10000"), secondMinute.getTradeVolume());
        assertEquals(new BigDecimal("150000000"), secondMinute.getCumulativeTradeAmount());
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
    void rejectsCoreKlineSchemaDrift() {
        FinanceHttpClient malformed = (provider, uri, headers) -> new FinanceHttpResponse(200,
                uri.getPath().contains("fflow/kline") ? "{\"data\":{\"klines\":[\"10:30,broken\"]}}" : "{\"data\":{}}",
                Instant.EPOCH, "hash");
        EastmoneyCapitalFlowProvider provider = new EastmoneyCapitalFlowProvider(malformed, Clock.systemUTC());
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
            byte[] bytes = Files.readAllBytes(Paths.get(getClass().getClassLoader()
                    .getResource("marketintel/" + fixture).toURI()));
            return new FinanceHttpResponse(200, new String(bytes, StandardCharsets.UTF_8), Instant.EPOCH, fixture);
        }
    }
}
