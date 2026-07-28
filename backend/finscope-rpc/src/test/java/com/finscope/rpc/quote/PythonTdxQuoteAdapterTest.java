package com.finscope.rpc.quote;

import com.finscope.domain.instrument.Quote;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonTdxQuoteAdapterTest {

    @Test
    void fetchesOnlyTheIndependentTdxFamilyAndMapsQuoteFacts() throws Exception {
        AtomicReference<URI> requested = new AtomicReference<URI>();
        FinanceHttpClient http = (providerCode, uri, headers) -> {
            requested.set(uri);
            String body = "{\"quality_status\":\"FRESH_PRIMARY\",\"source_code\":\"PYTDX\","
                    + "\"source_family\":\"TDX\",\"data\":{\"price\":1320.0,"
                    + "\"previous_close\":1289.5,\"open\":1299.0,\"high\":1320.0,"
                    + "\"low\":1289.52,\"change\":30.5,\"change_pct\":2.3653,"
                    + "\"volume\":53134,\"amount\":6960058368,"
                    + "\"observed_at\":\"2026-07-28T15:17:18.006+08:00\"}}";
            return new FinanceHttpResponse(200, body, Instant.parse("2026-07-28T07:17:19Z"), "hash");
        };
        PythonTdxQuoteAdapter adapter = new PythonTdxQuoteAdapter(
                "http://python-market-data:8000/", http);

        List<Quote> quotes = adapter.fetch(Collections.singletonList("600519"));

        assertEquals("/v1/stocks/SH/600519/quote", requested.get().getPath());
        assertEquals("provider_family=TDX&provider_mode=true", requested.get().getQuery());
        assertEquals(1, quotes.size());
        Quote quote = quotes.get(0);
        assertEquals("600519", quote.getInstrumentCode());
        assertEquals(1320.0, quote.getPrice());
        assertEquals(1289.5, quote.getPreviousClose());
        assertEquals(6_960_058_368.0, quote.getTurnover());
        assertEquals(2026, quote.getQuoteTime().getYear());
        assertTrue(quote.isValid());
    }
}
