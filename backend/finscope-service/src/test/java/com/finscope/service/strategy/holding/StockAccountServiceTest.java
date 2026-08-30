package com.finscope.service.strategy.holding;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.strategy.holding.StockAccountSnapshot;
import com.finscope.domain.strategy.holding.StockPosition;
import com.finscope.service.instrument.QuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockAccountServiceTest {
    @Test
    void valuesActualPositionWithRawQuoteInsteadOfCostOrAdjustedPrice() {
        StockTransactionService transactions = mock(StockTransactionService.class);
        QuoteService quotes = mock(QuoteService.class);
        StockPosition position = new StockPosition();
        position.setInstrumentId(1L);
        position.setInstrumentCode("600570.SH");
        position.setInstrumentName("恒生电子");
        position.setQuantity(new BigDecimal("100"));
        position.setTotalCost(new BigDecimal("2500"));
        position.setAverageCost(new BigDecimal("25"));
        StockAccountSnapshot replayed = new StockAccountSnapshot();
        replayed.setCash(new BigDecimal("1000"));
        replayed.setPositions(Collections.singletonList(position));
        when(transactions.account()).thenReturn(replayed);
        Quote quote = new Quote();
        quote.setInstrumentCode("600570");
        quote.setPrice(30d);
        quote.setValid(true);
        quote.setAsOf(LocalDateTime.of(2026, 8, 31, 15, 0));
        when(quotes.fetch("STOCK", Collections.singletonList("600570"), false))
                .thenReturn(Collections.singletonList(quote));
        StockAccountService service = new StockAccountService();
        ReflectionTestUtils.setField(service, "transactions", transactions);
        ReflectionTestUtils.setField(service, "quotes", quotes);

        StockAccountSnapshot result = service.snapshot();

        assertEquals(0, new BigDecimal("3000").compareTo(result.getMarketValue()));
        assertEquals(0, new BigDecimal("500").compareTo(result.getUnrealizedProfit()));
        assertEquals(0, new BigDecimal("4000").compareTo(result.getTotalEquity()));
        assertEquals("RAW_QUOTE", result.getPositions().get(0).getQuoteQuality());
    }
}
