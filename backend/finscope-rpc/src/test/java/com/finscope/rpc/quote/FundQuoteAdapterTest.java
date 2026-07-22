package com.finscope.rpc.quote;

import com.finscope.domain.instrument.Quote;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FundQuoteAdapterTest {

    @Test
    void keepsLatestConfirmedNavFreshWhenIntradayEstimateEndpointIsUnavailable() throws Exception {
        String history = "var fS_name = \"易方达半导体设备ETF联接C\";"
                + "var Data_netWorthTrend = ["
                + "{\"x\":1784476800000,\"y\":2.2881,\"equityReturn\":-5.45},"
                + "{\"x\":1784563200000,\"y\":2.6222,\"equityReturn\":14.6}];";
        FundQuoteAdapter adapter = new FundQuoteAdapter(url -> {
            if (url.contains("pingzhongdata")) return history;
            throw new IllegalStateException("HTTP 404");
        }, Runnable::run);

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
