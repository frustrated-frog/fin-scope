package com.finscope.rpc.quote;

import com.finscope.domain.instrument.Quote;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SinaIndexQuoteAdapterTest {

    @Test
    void parsesSinaIndexLineIntoQuote() {
        Quote quote = SinaIndexQuoteAdapter.parseLine(
                "var hq_str_s_sh000001=\"上证指数,3200.00,12.50,0.39,0,0\";"
        );

        assertEquals("000001", quote.getInstrumentCode());
        assertEquals("上证指数", quote.getName());
        assertEquals(3200.00, quote.getPrice());
        assertEquals(12.50, quote.getChangeAmount());
        assertEquals(0.39, quote.getChangePct());
        assertTrue(quote.isValid());
    }

    @Test
    void returnsInvalidQuoteForEmptyPayload() {
        Quote quote = SinaIndexQuoteAdapter.parseLine("var hq_str_s_sh000001=\"\";");

        assertEquals("000001", quote.getInstrumentCode());
        assertFalse(quote.isValid());
        assertEquals("未取到有效行情", quote.getNote());
    }
}
