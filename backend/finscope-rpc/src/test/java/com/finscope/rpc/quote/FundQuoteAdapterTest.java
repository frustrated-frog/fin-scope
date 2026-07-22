package com.finscope.rpc.quote;

import com.finscope.domain.instrument.Quote;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FundQuoteAdapterTest {

    @Test
    void fetchesMultipleFundValuationsInOneRequest() throws Exception {
        String payload = "{\"data\":["
                + "{\"NAV\":2.6222,\"NAVCHGRT\":14.6,\"GZTIME\":\"2026-07-22 10:33\","
                + "\"SHORTNAME\":\"易方达半导体设备ETF联接C\",\"FCODE\":\"021894\","
                + "\"PDATE\":\"2026-07-21\",\"GSZZL\":0.38,\"GSZ\":2.6322},"
                + "{\"NAV\":4.0235,\"NAVCHGRT\":12.67,\"GZTIME\":\"2026-07-22 10:33\","
                + "\"SHORTNAME\":\"南方上证科创板芯片ETF发起联接C\",\"FCODE\":\"021608\","
                + "\"PDATE\":\"2026-07-21\",\"GSZZL\":0.6,\"GSZ\":4.0476}],"
                + "\"errorCode\":0,\"success\":true}";
        List<String> requestedUrls = new ArrayList<String>();
        FundQuoteAdapter adapter = new FundQuoteAdapter(url -> {
            requestedUrls.add(url);
            if (url.contains("FundValuationLast")) return payload;
            throw new IllegalStateException("unexpected URL: " + url);
        });

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
        });

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
        FundNavHistoryAdapter adapter = new FundNavHistoryAdapter(url -> history, Runnable::run);

        Quote quote = adapter.fetch(Collections.singletonList("021894")).get(0);

        assertTrue(quote.isValid());
        assertEquals("易方达半导体设备ETF联接C", quote.getName());
        assertEquals(2.6222, quote.getConfirmedNav());
        assertEquals("2026-07-21", quote.getConfirmedNavDate());
        assertEquals(14.6, quote.getConfirmedNavChangePct());
        assertNull(quote.getPrice());
        assertNull(quote.getChangePct());
        assertTrue(quote.getNote().contains("盘中估值暂不可用"));
    }
}
