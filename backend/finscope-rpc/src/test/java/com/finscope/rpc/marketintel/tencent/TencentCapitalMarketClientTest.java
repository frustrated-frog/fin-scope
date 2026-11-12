package com.finscope.rpc.marketintel.tencent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TencentCapitalMarketClientTest {
    @Test
    void mapsQuoteUnitsRatiosAndSourceTimestamp() {
        String[] fields = new String[50];
        Arrays.fill(fields, "");
        fields[2] = "002185";
        fields[3] = "14.50";
        fields[30] = "20261112135000";
        fields[36] = "1200";
        fields[37] = "174.50";
        fields[38] = "2.45";
        fields[49] = "1.12";
        CapitalFlowPoint point = client((provider, uri, headers) -> {
            assertEquals("/q=sz002185", uri.getPath());
            return response("v_sz002185=\"" + String.join("~", fields) + "\";");
        }).fetchQuote(TencentCapitalReplayProviderTest.stock());
        assertEquals(new BigDecimal("1200"), point.getTradeVolume());
        assertEquals(new BigDecimal("1745000.00"), point.getCumulativeTradeAmount());
        assertEquals(new BigDecimal("2.45"), point.getTurnoverRate());
        assertEquals(new BigDecimal("1.12"), point.getVolumeRatio());
        assertEquals("2026-11-12T13:50", point.getObservedAt().toString());
        assertEquals("QUOTE", point.getGranularity());
        assertNull(point.getMainNetInflow());
        assertNull(point.getIntervalTradeAmount());
    }

    @Test
    void readsUnadjustedDailyActualAmountsAndKeepsMissingValuesNull() {
        List<CapitalFlowPoint> points = client((provider, uri, headers) -> {
            assertEquals("param=sz002185,day,,,320,", uri.getQuery());
            return response("""
                    {"code":0,"data":{"sz002185":{"day":[
                      ["2026-11-10","14","14.5","15","13","100",{},"2.4","145"],
                      ["2026-11-11","15","15.5","16","14","120",{},"","--"]
                    ],"qfqday":[["2026-11-10","1","2"]]}}}
                    """);
        }).fetchDailyHistory(TencentCapitalReplayProviderTest.stock());
        assertEquals(2, points.size());
        assertEquals(new BigDecimal("14.5"), points.get(0).getPrice());
        assertEquals(new BigDecimal("1450000"), points.get(0).getIntervalTradeAmount());
        assertEquals(new BigDecimal("100"), points.get(0).getTradeVolume());
        assertNull(points.get(1).getIntervalTradeAmount());
        assertNull(points.get(1).getTurnoverRate());
        assertNull(points.get(0).getMainNetInflow());
    }

    @Test
    void rejectsMalformedDuplicateAndUnsupportedDataAndPreservesTransportFailure() {
        for (String body : List.of("{}", "{\"code\":0,\"data\":{\"sz002185\":{\"day\":[[\"2026-11-10\",\"14\",\"-1\",\"15\",\"13\",\"100\",{},\"2\",\"145\"]]}}}")) {
            assertEquals("SCHEMA_DRIFT", assertThrows(ProviderContractException.class,
                    () -> client((p, u, h) -> response(body)).fetchDailyHistory(TencentCapitalReplayProviderTest.stock())).getErrorType());
        }
        assertEquals("CAPITAL_QUOTE_UNAVAILABLE", assertThrows(ProviderContractException.class,
                () -> client((p, u, h) -> { throw new IOException("offline"); })
                        .fetchQuote(TencentCapitalReplayProviderTest.stock())).getErrorType());
        var unsupported = TencentCapitalReplayProviderTest.stock();
        unsupported.setMarket("BJ");
        assertEquals("UNSUPPORTED_INSTRUMENT", assertThrows(ProviderContractException.class,
                () -> client((p, u, h) -> response("{}")).fetchQuote(unsupported)).getErrorType());
    }

    static TencentCapitalMarketClient client(FinanceHttpClient http) {
        TencentCapitalMarketClient client = new TencentCapitalMarketClient();
        ReflectionTestUtils.setField(client, "http", http);
        ReflectionTestUtils.setField(client, "json", new ObjectMapper());
        return client;
    }

    private static FinanceHttpResponse response(String body) {
        return new FinanceHttpResponse(200, body, Instant.parse("2026-11-12T06:00:00Z"), "fixture-hash");
    }
}
