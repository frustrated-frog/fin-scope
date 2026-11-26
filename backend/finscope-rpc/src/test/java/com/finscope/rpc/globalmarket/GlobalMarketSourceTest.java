package com.finscope.rpc.globalmarket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.globalmarket.GlobalMarketSeries;
import com.finscope.rpc.quote.QuoteHttpTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GlobalMarketSourceTest {
    private final GlobalMarketSource source = new GlobalMarketSource();
    private final QuoteHttpTransport http = mock(QuoteHttpTransport.class);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(source, "transport", http);
        ReflectionTestUtils.setField(source, "json", new ObjectMapper());
        for (String key : new String[]{"chartUrl", "coinbaseUrl", "goldHistoryUrl", "goldQuoteUrl"}) {
            ReflectionTestUtils.setField(source, key, "https://fixture/" + key + "/");
        }
    }

    private void response(String url, String text) {
        when(http.get(anyString(), eq(URI.create(url)), anyInt(), anyInt(), anyMap(), any(Charset.class))).thenReturn(text);
    }

    @Test
    void indexUsesPreviousSessionNotRangeBaselineAndDropsUnfinishedSession() throws Exception {
        String chart = """
                {"chart":{"result":[{"meta":{"symbol":"^GSPC","instrumentType":"INDEX","currency":"USD",
                "exchangeTimezoneName":"America/New_York","regularMarketTime":1704294000,
                "regularMarketPrice":115,"chartPreviousClose":10,"currentTradingPeriod":{"regular":{"end":1704315600}}},
                "timestamp":[1703856600,1704202200,1704294000],"indicators":{"quote":[{"close":[100,110,115]}]}}]}}
                """;
        response("https://fixture/chartUrl/%5EGSPC?interval=1d&range=1y", chart);
        GlobalMarketSeries data = source.fetch("SPX");
        assertEquals(110, data.reference());
        assertEquals(2, data.closes().size());
        response("https://fixture/chartUrl/%5EGSPC?interval=1d&range=1y", chart.replace("INDEX", "ETF"));
        assertThrows(IOException.class, () -> source.fetch("SPX"));
    }

    @Test
    void cryptoUsesRollingReferenceAndClosedUtcCandles() throws Exception {
        String base = "https://fixture/coinbaseUrl//products/BTC-USD";
        response(base + "/ticker", "{\"time\":\"2024-01-03T01:00:00Z\",\"price\":\"90\"}");
        response(base + "/stats", "{\"open\":\"95\"}");
        response(base + "/candles?granularity=86400", "[[1704240000,1,1,1,90],[1704153600,1,1,1,89],[1704067200,1,1,1,88]]");
        GlobalMarketSeries data = source.fetch("BTC");
        assertEquals(95, data.reference());
        assertEquals(Map.of("2024-01-02", 89d, "2024-01-01", 88d), data.closes());
    }

    @Test
    void goldUsesSpotQuoteDateAndProviderPriorClose() throws Exception {
        response("https://fixture/goldQuoteUrl/", "var hq_str_hf_XAU=\"2010,0,0,0,0,0,13:00:00,2000,0,0,0,0,2024-01-03,伦敦金\";");
        response("https://fixture/goldHistoryUrl/", "[{\"date\":\"2024-01-01\",\"close\":1998},{\"date\":\"2024-01-02\",\"close\":2000}]");
        GlobalMarketSeries data = source.fetch("XAU");
        assertEquals("2024-01-03T05:00:00Z", data.asOf());
        assertEquals(2000, data.reference());
        assertEquals(2010, data.price());
    }
}
