package com.finscope.service.marketdata;

import com.finscope.domain.instrument.Quote;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteQualityValidatorTest {

    private final QuoteQualityValidator validator = new QuoteQualityValidator(Clock.fixed(
            LocalDateTime.of(2026, 7, 22, 10, 30).toInstant(ZoneOffset.ofHours(8)),
            ZoneOffset.ofHours(8)));

    @Test
    void acceptsFundQuoteWithLatestConfirmedNavWhenIntradayEstimateIsUnavailable() {
        Quote quote = new Quote();
        quote.setInstrumentCode("021894");
        quote.setConfirmedNav(2.6222);
        quote.setConfirmedNavDate("2026-07-21");
        quote.setConfirmedNavChangePct(14.60);
        quote.setValid(true);

        assertTrue(validator.accept("021894", quote).isPresent());
    }
}
