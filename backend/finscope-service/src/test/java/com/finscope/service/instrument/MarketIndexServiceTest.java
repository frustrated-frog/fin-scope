package com.finscope.service.instrument;

import com.finscope.domain.instrument.Quote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

class MarketIndexServiceTest {
    @Mock
    private QuoteService quoteService;

    private MarketIndexService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new MarketIndexService();
        ReflectionTestUtils.setField(service, "quoteService", quoteService);
    }

    @Test
    void returnsTheFourMarketIndicesInProductOrder() {
        when(quoteService.fetch("INDEX", Arrays.asList("000001", "399001", "399006", "000688")))
                .thenReturn(Collections.singletonList(validQuote("000001", 3200.00, 12.50, 0.39)));

        List<MarketIndexView> result = service.list();

        assertEquals(Arrays.asList("000001", "399001", "399006", "000688"),
                result.stream().map(MarketIndexView::getCode).collect(Collectors.toList()));
        assertEquals("上证指数", result.get(0).getName());
        assertFalse(result.get(2).getQuote().isValid());
    }

    private Quote validQuote(String code, double price, double changeAmount, double changePct) {
        Quote quote = new Quote();
        quote.setInstrumentCode(code);
        quote.setPrice(price);
        quote.setChangeAmount(changeAmount);
        quote.setChangePct(changePct);
        quote.setValid(true);
        return quote;
    }
}
