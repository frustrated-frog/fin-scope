package com.finscope.rpc.quote;

import com.finscope.domain.instrument.Quote;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SinaIndexQuoteAdapterTest {
    @Test
    void preservesSourceTimeForStocksAndIndices() {
        String[] fields = new String[32];
        Arrays.fill(fields, "0");
        fields[0] = "上证指数";
        fields[1] = "3190";
        fields[2] = "3187.50";
        fields[3] = "3200";
        fields[4] = "3210";
        fields[5] = "3180";
        fields[30] = "2026-10-09";
        fields[31] = "15:00:00";
        String line = "var hq_str_sh000001=\"" + String.join(",", fields) + "\";";
        Quote index = SinaIndexQuoteAdapter.parseLine(line);
        Quote stock = SinaStockQuoteAdapter.parseLine(line.replace("sh000001", "sz000001"));
        assertEquals("000001", index.getInstrumentCode());
        assertEquals(3200D, index.getPrice());
        assertEquals(12.5D, index.getChangeAmount());
        assertEquals(LocalDateTime.of(2026, 10, 9, 15, 0), index.getQuoteTime());
        assertEquals(index.getQuoteTime(), stock.getQuoteTime());
        assertTrue(index.isValid());
    }

    @Test
    void rejectsTimestampFreeShortResponse() {
        Quote quote = SinaIndexQuoteAdapter.parseLine(
                "var hq_str_sh000001=\"上证指数,3200,12.5,0.39\";");
        assertFalse(quote.isValid());
        assertNull(quote.getQuoteTime());
    }

    @Test
    void invalidSourceDateNeverFallsBackToCurrentTime() {
        String[] fields = new String[32];
        Arrays.fill(fields, "0");
        fields[0] = "样本";
        fields[2] = "10";
        fields[3] = "10";
        fields[30] = "invalid";
        fields[31] = "15:00:00";
        Quote quote = SinaStockQuoteAdapter.parseLine(
                "var hq_str_sh600519=\"" + String.join(",", fields) + "\";");
        assertFalse(quote.isValid());
        assertNull(quote.getQuoteTime());
    }

    @Test
    void returnsInvalidQuoteForEmptyPayload() {
        Quote quote = SinaIndexQuoteAdapter.parseLine("var hq_str_sh000001=\"\";");
        assertEquals("000001", quote.getInstrumentCode());
        assertFalse(quote.isValid());
    }
}
