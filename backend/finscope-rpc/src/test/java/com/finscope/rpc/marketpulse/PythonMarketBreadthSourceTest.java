package com.finscope.rpc.marketpulse;

import com.finscope.domain.marketpulse.MarketBreadthSnapshot;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonMarketBreadthSourceTest {

    @Test
    void parsesVersionedMarketBreadthAndPreservesProvenance() {
        AtomicReference<URI> requested = new AtomicReference<URI>();
        FinanceHttpClient http = (provider, uri, headers) -> {
            requested.set(uri);
            return response(payload("FRESH_FALLBACK", "2026-08-21"));
        };
        PythonMarketBreadthSource source = source("http://127.0.0.1:8000/", http);

        MarketBreadthSnapshot result = source.fetch(LocalDate.of(2026, 8, 21));

        assertEquals("/v1/markets/CN-A/breadth", requested.get().getPath());
        assertEquals("business_date=2026-08-21", requested.get().getQuery());
        assertEquals(LocalDate.of(2026, 8, 21), result.getBusinessDate());
        assertEquals("AKSHARE_SINA_A_SPOT", result.getSourceCode());
        assertEquals("SINA", result.getSourceFamily());
        assertEquals("FRESH_FALLBACK", result.getQualityStatus());
        assertEquals(3200, result.getAdvanceCount());
        assertEquals(1800, result.getDeclineCount());
        assertEquals(100, result.getFlatCount());
        assertEquals(5100, result.getValidCount());
        assertEquals(3200D / 5100D, result.getAdvanceRatio());
        assertEquals(2_300_000_000_000D, result.getTotalAmount());
        assertEquals(68, result.getLimitUpCount());
        assertEquals(4, result.getLimitDownCount());
        assertEquals(0.7D, result.getMedianChangePct());
        assertEquals(7, result.getReturnDistribution().size());
        assertEquals("UP_3_7", result.getReturnDistribution().get(5).getCode());
        assertEquals(320, result.getReturnDistribution().get(5).getCount());
        assertEquals(0.61D, result.getTrendBreadth().getMa20Ratio());
        assertEquals(5080, result.getTrendBreadth().getMa20ValidCount());
        assertEquals(88, result.getNewHighLow().getHigh20Count());
        assertEquals(23, result.getNewHighLow().getLow20Count());
        assertEquals(1400, result.getNetAdvances());
        assertEquals(8600, result.getAdvanceDeclineLine());
        assertEquals(2, result.getHistory().size());
        assertEquals(LocalDate.of(2026, 8, 20), result.getHistory().get(0).getBusinessDate());
        assertEquals(0.52D, result.getHistory().get(0).getAdvanceRatio());
        assertEquals(0.61D, result.getHistory().get(1).getMa20Ratio());
        assertEquals(8600, result.getHistory().get(1).getAdvanceDeclineLine());
        assertTrue(result.getWarnings().contains("东方财富不可用"));
    }

    @Test
    void rejectsSchemaDriftAndBusinessDateMismatch() {
        PythonMarketBreadthSource schemaDrift = source(
                payload("FRESH_PRIMARY", "2026-08-21")
                        .replace("market-breadth-v2", "market-breadth-v3"));
        PythonMarketBreadthSource dateMismatch = source(
                payload("FRESH_PRIMARY", "2026-08-20"));

        assertEquals("MARKET_BREADTH_SCHEMA_DRIFT", assertThrows(
                ProviderContractException.class,
                () -> schemaDrift.fetch(LocalDate.of(2026, 8, 21))).getErrorType());
        assertEquals("MARKET_BREADTH_DATE_MISMATCH", assertThrows(
                ProviderContractException.class,
                () -> dateMismatch.fetch(LocalDate.of(2026, 8, 21))).getErrorType());
    }

    private PythonMarketBreadthSource source(String body) {
        return source("http://127.0.0.1:8000",
                (provider, uri, headers) -> response(body));
    }

    private PythonMarketBreadthSource source(String baseUrl, FinanceHttpClient http) {
        PythonMarketBreadthSource source = new PythonMarketBreadthSource();
        ReflectionTestUtils.setField(source, "baseUrl", baseUrl);
        ReflectionTestUtils.setField(source, "http", http);
        return source;
    }

    private static FinanceHttpResponse response(String body) {
        return new FinanceHttpResponse(200, body,
                Instant.parse("2026-08-21T07:20:00Z"), "hash");
    }

    private static String payload(String quality, String date) {
        return "{\"schema_version\":\"market-breadth-v2\",\"market\":\"CN-A\","
                + "\"business_date\":\"" + date + "\","
                + "\"source_code\":\"AKSHARE_SINA_A_SPOT\",\"source_family\":\"SINA\","
                + "\"quality_status\":\"" + quality + "\","
                + "\"retrieved_at\":\"2026-08-21T15:20:00+08:00\","
                + "\"advance_count\":3200,\"decline_count\":1800,\"flat_count\":100,"
                + "\"valid_count\":5100,\"advance_ratio\":0.6274509803921569,"
                + "\"total_amount\":2300000000000,\"limit_up_count\":68,"
                + "\"limit_down_count\":4,\"median_change_pct\":0.7,"
                + "\"return_distribution\":["
                + "{\"code\":\"DOWN_7\",\"label\":\"≤ -7%\",\"lower_bound\":null,\"upper_bound\":-7,\"count\":80,\"ratio\":0.0156862745},"
                + "{\"code\":\"DOWN_3_7\",\"label\":\"-7% ~ -3%\",\"lower_bound\":-7,\"upper_bound\":-3,\"count\":420,\"ratio\":0.0823529412},"
                + "{\"code\":\"DOWN_0_3\",\"label\":\"-3% ~ 0\",\"lower_bound\":-3,\"upper_bound\":0,\"count\":1300,\"ratio\":0.2549019608},"
                + "{\"code\":\"FLAT\",\"label\":\"0\",\"lower_bound\":0,\"upper_bound\":0,\"count\":100,\"ratio\":0.0196078431},"
                + "{\"code\":\"UP_0_3\",\"label\":\"0 ~ 3%\",\"lower_bound\":0,\"upper_bound\":3,\"count\":2480,\"ratio\":0.4862745098},"
                + "{\"code\":\"UP_3_7\",\"label\":\"3% ~ 7%\",\"lower_bound\":3,\"upper_bound\":7,\"count\":320,\"ratio\":0.062745098},"
                + "{\"code\":\"UP_7\",\"label\":\"≥ 7%\",\"lower_bound\":7,\"upper_bound\":null,\"count\":400,\"ratio\":0.0784313725}],"
                + "\"trend_breadth\":{\"ma20_ratio\":0.61,\"ma20_valid_count\":5080,\"ma60_ratio\":0.56,\"ma60_valid_count\":5020,\"ma120_ratio\":0.51,\"ma120_valid_count\":4950,\"ma250_ratio\":0.47,\"ma250_valid_count\":4800},"
                + "\"new_high_low\":{\"high20_count\":88,\"low20_count\":23,\"valid20_count\":5080,\"high60_count\":51,\"low60_count\":19,\"valid60_count\":5020,\"high250_count\":32,\"low250_count\":14,\"valid250_count\":4800},"
                + "\"net_advances\":1400,\"advance_decline_line\":8600,"
                + "\"history\":["
                + history("2026-08-20", 0.52, 0.57, 7200, 52, 31)
                + "," + history("2026-08-21", 0.6274509803921569, 0.61, 8600, 88, 23)
                + "],"
                + "\"warnings\":[\"东方财富不可用\"]}";
    }

    private static String history(String date, double advanceRatio, double ma20,
                                  int adLine, int high20, int low20) {
        return "{\"business_date\":\"" + date + "\",\"advance_count\":2600,"
                + "\"decline_count\":2400,\"flat_count\":100,\"valid_count\":5100,"
                + "\"advance_ratio\":" + advanceRatio + ",\"total_amount\":2200000000000,"
                + "\"median_change_pct\":0.2,\"ma20_ratio\":" + ma20 + ","
                + "\"ma60_ratio\":0.55,\"ma120_ratio\":0.5,\"ma250_ratio\":0.46,"
                + "\"new_high20_count\":" + high20 + ",\"new_low20_count\":" + low20 + ","
                + "\"new_high60_count\":42,\"new_low60_count\":20,"
                + "\"new_high250_count\":28,\"new_low250_count\":15,"
                + "\"net_advances\":200,\"advance_decline_line\":" + adLine + "}";
    }
}
