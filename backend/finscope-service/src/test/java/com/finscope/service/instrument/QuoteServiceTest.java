package com.finscope.service.instrument;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.service.marketdata.MarketDataGateway;
import com.finscope.service.marketdata.QuoteGatewayResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuoteServiceTest {

    @Test
    void delegatesNormalAndForcedRefreshesToTheGateway() {
        MarketDataGateway gateway = mock(MarketDataGateway.class);
        Quote quote = quote("600519", MarketDataQualityStatus.FRESH_FALLBACK, "SINA_STOCK");
        when(gateway.fetchQuotes("STOCK", Collections.singletonList("600519"), false))
                .thenReturn(result(quote));
        when(gateway.fetchQuotes("STOCK", Collections.singletonList("600519"), true))
                .thenReturn(result(quote));
        QuoteService service = new QuoteService(gateway);

        List<Quote> normal = service.fetch("STOCK", Collections.singletonList("600519"));
        List<Quote> forced = service.fetch("STOCK", Collections.singletonList("600519"), true);

        assertEquals("SINA_STOCK", normal.get(0).getSourceCode());
        assertEquals(MarketDataQualityStatus.FRESH_FALLBACK, forced.get(0).getQualityStatus());
        verify(gateway).fetchQuotes("STOCK", Collections.singletonList("600519"), false);
        verify(gateway).fetchQuotes("STOCK", Collections.singletonList("600519"), true);
    }

    private Quote quote(String code, MarketDataQualityStatus status, String source) {
        Quote quote = new Quote();
        quote.setInstrumentCode(code);
        quote.setPrice(100.0);
        quote.setValid(true);
        quote.setQualityStatus(status);
        quote.setSourceCode(source);
        quote.setAsOf(LocalDateTime.of(2026, 7, 14, 10, 0));
        return quote;
    }

    private QuoteGatewayResult result(Quote quote) {
        return new QuoteGatewayResult(Collections.singletonList(quote), quote.getQualityStatus(),
                quote.getSourceCode(), quote.getAsOf(), quote.getRetrievedAt(),
                quote.getStaleAgeSeconds(), quote.getWarning(), "r-1");
    }
}
