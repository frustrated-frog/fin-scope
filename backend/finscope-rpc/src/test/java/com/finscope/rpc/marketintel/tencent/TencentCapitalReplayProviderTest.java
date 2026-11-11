package com.finscope.rpc.marketintel.tencent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.marketintel.CapitalReplayData;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TencentCapitalReplayProviderTest {
    @Test
    void mapsFiveDayEnvelopeAndDifferencesCumulativeTurnoverWithoutInventingFlow() {
        TencentCapitalReplayProvider provider = provider((source, uri, headers) -> {
            assertEquals("TENCENT_INTRADAY", source);
            assertEquals("/appstock/app/day/query", uri.getPath());
            assertEquals("code=sz002185", uri.getQuery());
            return response("""
                    {"code":0,"data":{"sz002185":{"data":[
                      {"date":"20261111","data":["0930 14.48 10 1000.00","0931 14.54 15 1600.00","1530 14.95 50 9000.00"]},
                      {"date":"20261110","data":["0930 14.71 2 200.00","0931 14.74 3 350.00"]}
                    ]}}}
                    """);
        });
        CapitalReplayData data = provider.fetchReplay(stock());
        assertEquals(4, data.getPrices().size());
        assertEquals(new BigDecimal("600.00"), data.getPrices().get(1).getIntervalTradeAmount());
        assertEquals(new BigDecimal("200.00"), data.getPrices().get(2).getIntervalTradeAmount());
        assertEquals(new BigDecimal("150.00"), data.getPrices().get(3).getIntervalTradeAmount());
        assertEquals("2026-11-11T09:31", data.getPrices().get(1).getObservedAt().toString());
        assertEquals("fixture-hash", data.getPrices().get(1).getPayloadHash());
        assertTrue(data.getFlows().isEmpty());
        assertNull(data.getPrices().get(0).getLargeNetInflow());
    }

    @Test
    void neverAssignsUnobservedTurnoverToOneMinuteAndHandlesLunchBoundary() {
        CapitalReplayData data = provider((source, uri, headers) -> response("""
                {"code":0,"data":{"sz002185":{"data":[{"date":"20261111","data":[
                  "0931 14.5 10 1000","0934 14.6 20 2000","0935 14.6 25 2500",
                  "1130 14.7 30 3000","1301 14.8 40 4000","1302 14.8 42 3900"
                ]}]}}}
                """)).fetchReplay(stock());
        assertNull(data.getPrices().get(0).getIntervalTradeAmount());
        assertNull(data.getPrices().get(1).getIntervalTradeAmount());
        assertEquals(new BigDecimal("500"), data.getPrices().get(2).getIntervalTradeAmount());
        assertEquals(new BigDecimal("1000"), data.getPrices().get(4).getIntervalTradeAmount());
        assertNull(data.getPrices().get(5).getIntervalTradeAmount());
        assertTrue(data.getWarnings().contains("INTRADAY_AMOUNT_GAP"));
    }

    @Test
    void translatesTransportFailureAndRejectsInvalidProtocolValues() {
        ProviderContractException failure = assertThrows(ProviderContractException.class,
                () -> provider((source, uri, headers) -> { throw new IOException("unavailable"); }).fetchReplay(stock()));
        assertEquals("INTRADAY_RESPONSE_ERROR", failure.getErrorType());
        for (String body : new String[]{"{}", "{broken", """
                {"code":0,"data":{"sz002185":{"data":[{"date":"20261111","data":["0930 -1 10 1000"]}]}}}
                """}) {
            ProviderContractException drift = assertThrows(ProviderContractException.class,
                    () -> provider((source, uri, headers) -> response(body)).fetchReplay(stock()));
            assertEquals("SCHEMA_DRIFT", drift.getErrorType());
        }
    }

    @Test
    void rejectsEmptyDataAndUnsupportedInstruments() {
        TencentCapitalReplayProvider provider = provider((source, uri, headers) -> response(
                "{\"code\":0,\"data\":{\"sz002185\":{\"data\":[]}}}"));
        assertEquals("EMPTY_INTRADAY_MARKET", assertThrows(ProviderContractException.class,
                () -> provider.fetchReplay(stock())).getErrorType());
        Instrument invalid = stock();
        invalid.setMarket("BJ");
        assertFalse(provider.supports(invalid));
        assertEquals("UNSUPPORTED_INSTRUMENT", assertThrows(ProviderContractException.class,
                () -> provider.fetchReplay(invalid)).getErrorType());
    }

    static TencentCapitalReplayProvider provider(FinanceHttpClient http) {
        TencentCapitalReplayProvider value = new TencentCapitalReplayProvider();
        ReflectionTestUtils.setField(value, "http", http);
        ReflectionTestUtils.setField(value, "json", new ObjectMapper());
        return value;
    }

    static Instrument stock() {
        Instrument stock = new Instrument();
        stock.setId(6L);
        stock.setType("STOCK");
        stock.setMarket("SZ");
        stock.setCode("002185");
        return stock;
    }

    private FinanceHttpResponse response(String body) {
        return new FinanceHttpResponse(200, body, Instant.parse("2026-11-11T08:00:00Z"), "fixture-hash");
    }
}
