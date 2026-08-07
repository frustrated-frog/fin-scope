package com.finscope.service.strategy;

import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.service.instrument.QuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrategyInstrumentResolverTest {
    @Test
    void resolvesTheNewBeijingExchange920PrefixWithoutMatchingAnotherMarket() {
        InstrumentRepository instruments = mock(InstrumentRepository.class);
        QuoteService quotes = mock(QuoteService.class);
        when(instruments.findByCodeTypeAndMarket("920001", "STOCK", "BJ"))
                .thenReturn(Optional.empty());
        when(quotes.fetch("STOCK", Collections.singletonList("920001")))
                .thenReturn(Collections.emptyList());
        when(instruments.save(any(Instrument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        StrategyInstrumentResolver resolver = new StrategyInstrumentResolver();
        ReflectionTestUtils.setField(resolver, "instrumentRepository", instruments);
        ReflectionTestUtils.setField(resolver, "quoteService", quotes);

        Instrument result = resolver.resolve("920001", "stock");

        assertEquals("BJ", result.getMarket());
    }
}
