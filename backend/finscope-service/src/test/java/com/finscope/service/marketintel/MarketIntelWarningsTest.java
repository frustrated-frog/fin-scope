package com.finscope.service.marketintel;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketIntelWarningsTest {
    @Test
    void splitsCompositeWarningsAndRemovesDuplicates() {
        List<String> warnings = MarketIntelWarnings.normalize(Arrays.asList(
                "DAILY_MARKET_UNAVAILABLE:CONNECTION_ERROR",
                "QUOTE_UNAVAILABLE:CONNECTION_ERROR",
                "QUOTE_UNAVAILABLE:CONNECTION_ERROR；DAILY_MARKET_UNAVAILABLE:CONNECTION_ERROR"
        ));

        assertEquals(Arrays.asList(
                "DAILY_MARKET_UNAVAILABLE:CONNECTION_ERROR",
                "QUOTE_UNAVAILABLE:CONNECTION_ERROR"
        ), warnings);
    }
}
