package com.finscope.service.instrument;

import com.finscope.domain.instrument.Quote;
import com.finscope.rpc.quote.QuoteAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuoteServiceTest {

    @Test
    public void reusesFreshQuoteSnapshotsInsteadOfCallingAdapterAgain() {
        CountingAdapter adapter = new CountingAdapter();
        QuoteService service = new QuoteService();
        ReflectionTestUtils.setField(service, "adapters", Collections.<QuoteAdapter>singletonList(adapter));

        service.fetch("STOCK", Collections.singletonList("600519"));
        service.fetch("STOCK", Collections.singletonList("600519"));

        assertEquals(1, adapter.callCount);
    }

    @Test
    public void forceRefreshBypassesFreshCacheAndCallsAdapterAgain() {
        CountingAdapter adapter = new CountingAdapter();
        QuoteService service = new QuoteService();
        ReflectionTestUtils.setField(service, "adapters", Collections.<QuoteAdapter>singletonList(adapter));

        service.fetch("STOCK", Collections.singletonList("600519"));
        service.fetch("STOCK", Collections.singletonList("600519"), true);

        assertEquals(2, adapter.callCount);
    }

    @Test
    public void routesSectorCodesToSectorAdapter() {
        CountingAdapter adapter = new CountingAdapter();
        adapter.supportedType = "SECTOR";
        QuoteService service = new QuoteService();
        ReflectionTestUtils.setField(service, "adapters", Arrays.<QuoteAdapter>asList(adapter));

        List<Quote> quotes = service.fetch("SECTOR", Collections.singletonList("BK0477"));

        assertEquals(1, adapter.callCount);
        assertTrue(quotes.get(0).isValid());
        assertEquals("BK0477", quotes.get(0).getInstrumentCode());
    }

    private static class CountingAdapter implements QuoteAdapter {
        private int callCount;
        private String supportedType = "STOCK";

        @Override
        public boolean supports(String instrumentType) {
            return supportedType.equals(instrumentType);
        }

        @Override
        public List<Quote> fetch(List<String> codes) {
            callCount++;
            Quote quote = new Quote();
            quote.setInstrumentCode(codes.get(0));
            quote.setPrice(100.0);
            quote.setValid(true);
            return Collections.singletonList(quote);
        }
    }
}
