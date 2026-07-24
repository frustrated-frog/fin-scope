package com.finscope.rpc.quote;

import com.finscope.domain.instrument.Quote;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FundQuoteAdapterTest {

    @Test
    void fetchesMultipleFundValuationsInOneRequest() throws Exception {
        String payload = "{\"data\":["
                + "{\"NAV\":4.0235,\"NAVCHGRT\":12.67,\"GZTIME\":\"2026-07-22 10:33\","
                + "\"SHORTNAME\":\"南方上证科创板芯片ETF发起联接C\",\"FCODE\":\"021608\","
                + "\"PDATE\":\"2026-07-21\",\"GSZZL\":0.6,\"GSZ\":4.0476},"
                + "{\"NAV\":2.6222,\"NAVCHGRT\":14.6,\"GZTIME\":\"2026-07-22 10:33\","
                + "\"SHORTNAME\":\"易方达半导体设备ETF联接C\",\"FCODE\":\"021894\","
                + "\"PDATE\":\"2026-07-21\",\"GSZZL\":0.38,\"GSZ\":2.6322}],"
                + "\"errorCode\":0,\"success\":true}";
        List<String> requestedUrls = new ArrayList<String>();
        FundQuoteAdapter adapter = new FundQuoteAdapter(url -> {
            requestedUrls.add(url);
            if (url.contains("FundValuationLast")) return payload;
            throw new IllegalStateException("unexpected URL: " + url);
        }, fixedClock("2026-07-22T02:34:00Z", "UTC"));

        List<Quote> quotes = adapter.fetch(Arrays.asList("021894", "021608"));

        assertEquals(1, requestedUrls.size());
        assertTrue(requestedUrls.get(0).contains("FCODES=021894%2C021608"));
        Quote quote = quotes.get(0);
        assertTrue(quote.isValid());
        assertEquals("021894", quote.getInstrumentCode());
        assertEquals("易方达半导体设备ETF联接C", quote.getName());
        assertEquals(2.6222, quote.getConfirmedNav());
        assertEquals("2026-07-21", quote.getConfirmedNavDate());
        assertEquals(14.6, quote.getConfirmedNavChangePct());
        assertEquals(2.6322, quote.getPrice());
        assertEquals(0.38, quote.getChangePct());
        assertTrue(quote.getNote().contains("2026-07-22 10:33"));
        assertEquals(LocalDateTime.of(2026, 7, 22, 10, 33), quote.getQuoteTime());
        assertEquals(LocalDateTime.of(2026, 7, 22, 2, 33), quote.getAsOf());
        assertEquals("021608", quotes.get(1).getInstrumentCode());
        assertEquals(4.0476, quotes.get(1).getPrice());
    }

    @Test
    void rejectsMalformedBusinessPayloadSoTheGatewayCanUseBackup() {
        FundQuoteAdapter adapter = new FundQuoteAdapter(
                url -> "{\"data\":{},\"success\":false}",
                fixedClock("2026-07-22T02:34:00Z", "UTC"));

        assertThrows(IOException.class,
                () -> adapter.fetch(Collections.singletonList("021894")));
    }

    @Test
    void keepsConfirmedNavButDropsEstimateWithoutATrustworthyCurrentTimestamp() throws Exception {
        String payload = "{\"data\":[{\"FCODE\":\"021894\",\"SHORTNAME\":\"半导体基金\","
                + "\"NAV\":2.6222,\"PDATE\":\"2026-07-21\",\"NAVCHGRT\":14.6,"
                + "\"GSZ\":2.6322,\"GSZZL\":0.38,\"GZTIME\":\"bad-time\"}],"
                + "\"success\":true}";
        FundQuoteAdapter adapter = new FundQuoteAdapter(url -> payload,
                fixedClock("2026-07-22T02:34:00Z", "UTC"));

        Quote quote = adapter.fetch(Collections.singletonList("021894")).get(0);

        assertTrue(quote.isValid());
        assertEquals(2.6222, quote.getConfirmedNav());
        assertNull(quote.getPrice());
        assertNull(quote.getChangePct());
        assertEquals(LocalDateTime.of(2026, 7, 21, 15, 0), quote.getQuoteTime());
        assertEquals(LocalDateTime.of(2026, 7, 21, 7, 0), quote.getAsOf());
    }

    @Test
    void dropsAnEstimateFromAPreviousTradingDate() throws Exception {
        String payload = "{\"data\":[{\"FCODE\":\"021894\",\"NAV\":2.6222,"
                + "\"PDATE\":\"2026-07-21\",\"GSZ\":2.6322,\"GSZZL\":0.38,"
                + "\"GZTIME\":\"2026-07-21 14:55\"}],\"success\":true}";
        FundQuoteAdapter adapter = new FundQuoteAdapter(url -> payload,
                fixedClock("2026-07-22T02:34:00Z", "UTC"));

        Quote quote = adapter.fetch(Collections.singletonList("021894")).get(0);

        assertTrue(quote.isValid());
        assertNull(quote.getPrice());
        assertNull(quote.getChangePct());
    }

    @Test
    void rejectsEstimateRowsWithoutAValidConfirmedNav() throws Exception {
        String payload = "{\"data\":[{\"FCODE\":\"021894\",\"NAV\":null,"
                + "\"PDATE\":\"2026-07-21\",\"GSZ\":2.6322,\"GSZZL\":0.38,"
                + "\"GZTIME\":\"2026-07-22 10:33\"}],\"success\":true}";
        FundQuoteAdapter adapter = new FundQuoteAdapter(url -> payload,
                fixedClock("2026-07-22T02:34:00Z", "UTC"));

        Quote quote = adapter.fetch(Collections.singletonList("021894")).get(0);

        assertFalse(quote.isValid());
    }

    @Test
    void preservesRequestedOrderAndMarksMissingBatchSymbolsUnavailable() throws Exception {
        String payload = "{\"data\":[{\"FCODE\":\"021608\",\"NAV\":4.0235,"
                + "\"PDATE\":\"2026-07-21\",\"GSZ\":4.0476,\"GSZZL\":0.6,"
                + "\"GZTIME\":\"2026-07-22 10:33\"}],\"success\":true}";
        FundQuoteAdapter adapter = new FundQuoteAdapter(url -> payload,
                fixedClock("2026-07-22T02:34:00Z", "UTC"));

        List<Quote> quotes = adapter.fetch(Arrays.asList("021894", "021608"));

        assertEquals("021894", quotes.get(0).getInstrumentCode());
        assertFalse(quotes.get(0).isValid());
        assertEquals("021608", quotes.get(1).getInstrumentCode());
        assertTrue(quotes.get(1).isValid());
    }

    @Test
    void backupAdapterUsesTheIndependentEastmoneyHost() throws Exception {
        String payload = "{\"data\":[{\"FCODE\":\"021894\",\"SHORTNAME\":\"半导体基金\","
                + "\"NAV\":2.6222,\"PDATE\":\"2026-07-21\",\"NAVCHGRT\":14.6,"
                + "\"GSZ\":2.6322,\"GSZZL\":0.38,\"GZTIME\":\"2026-07-22 10:33\"}],"
                + "\"success\":true}";
        List<String> requestedUrls = new ArrayList<String>();
        FundQuoteBackupAdapter adapter = new FundQuoteBackupAdapter(url -> {
            requestedUrls.add(url);
            return payload;
        }, fixedClock("2026-07-22T02:34:00Z", "UTC"));

        Quote quote = adapter.fetch(Collections.singletonList("021894")).get(0);

        assertEquals(1, requestedUrls.size());
        assertTrue(requestedUrls.get(0).startsWith(
                "https://fundcomapi.eastmoney.com/mm/newCore/FundValuationLast"));
        assertEquals(2.6322, quote.getPrice());
    }

    @Test
    void confirmedNavFallbackKeepsLatestOfficialValueAvailable() throws Exception {
        String history = "var fS_name = \"易方达半导体设备ETF联接C\";"
                + "var Data_netWorthTrend = ["
                + "{\"x\":1784476800000,\"y\":2.2881,\"equityReturn\":-5.45},"
                + "{\"x\":1784563200000,\"y\":2.6222,\"equityReturn\":14.6}];";
        FundNavHistoryAdapter adapter = new FundNavHistoryAdapter(url -> history, Runnable::run,
                fixedClock("2026-07-22T02:34:00Z", "Asia/Shanghai"));

        Quote quote = adapter.fetch(Collections.singletonList("021894")).get(0);

        assertTrue(quote.isValid());
        assertEquals("易方达半导体设备ETF联接C", quote.getName());
        assertEquals(2.6222, quote.getConfirmedNav());
        assertEquals("2026-07-21", quote.getConfirmedNavDate());
        assertEquals(14.6, quote.getConfirmedNavChangePct());
        assertNull(quote.getPrice());
        assertNull(quote.getChangePct());
        assertEquals(LocalDateTime.of(2026, 7, 21, 15, 0), quote.getQuoteTime());
        assertTrue(quote.getNote().contains("盘中估值暂不可用"));
    }

    @Test
    void historyProviderPropagatesTotalTransportFailureForCircuitBreaking() {
        FundNavHistoryAdapter adapter = new FundNavHistoryAdapter(url -> {
            throw new IOException("history host down");
        }, Runnable::run, fixedClock("2026-07-22T02:34:00Z", "Asia/Shanghai"));

        assertThrows(IOException.class,
                () -> adapter.fetch(Collections.singletonList("021894")));
    }

    @Test
    void historyProviderTreatsATotalBatchTimeoutAsAProviderFailure() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            FundNavHistoryAdapter adapter = new FundNavHistoryAdapter(url -> {
                Thread.sleep(5000L);
                return "";
            }, executor, fixedClock("2026-07-22T02:34:00Z", "Asia/Shanghai"));

            assertThrows(IOException.class,
                    () -> adapter.fetch(Collections.singletonList("021894")));
        } finally {
            executor.shutdownNow();
        }
    }

    private Clock fixedClock(String instant, String zone) {
        return Clock.fixed(Instant.parse(instant), ZoneId.of(zone));
    }
}
