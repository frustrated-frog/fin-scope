package com.finscope.rpc.quote;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TencentQuoteProviderTest {

    @Test
    void parsesStockAndIndexUsingVerifiedTencentFieldIndexes() throws Exception {
        TencentQuoteParser parser = new TencentQuoteParser(url -> fixture("tencent-stock-index.txt"));
        TencentStockQuoteAdapter stocks = new TencentStockQuoteAdapter(parser);
        TencentIndexQuoteAdapter indices = new TencentIndexQuoteAdapter(parser);
        List<Quote> quotes = new ArrayList<Quote>();
        quotes.addAll(stocks.fetch(Collections.singletonList("600519")));
        quotes.addAll(indices.fetch(Collections.singletonList("399006")));

        Quote stock = byCode(quotes, "600519");
        assertEquals(1480.00, stock.getPrice());
        assertEquals(1471.20, stock.getPreviousClose());
        assertEquals(8.80, stock.getChangeAmount());
        assertEquals(0.60, stock.getChangePct());
        assertEquals(1488.00, stock.getHigh());
        assertEquals(1468.10, stock.getLow());
        assertEquals(1870400000.0, stock.getTurnover());
        assertEquals(12345600.0, stock.getVolume());
        assertEquals(1.35, stock.getAmplitude());
        assertTrue(byCode(quotes, "399006").isValid());
        assertEquals("TENCENT_STOCK", stocks.providerCode());
        assertTrue(indices.capabilities().contains(MarketDataCapability.REALTIME_INDEX_QUOTE));
    }

    @Test
    void rejectsHtmlAndRowsShorterThanTencentContract() {
        TencentStockQuoteAdapter html = new TencentStockQuoteAdapter(
                new TencentQuoteParser(url -> "<html>blocked</html>"));
        TencentStockQuoteAdapter shortRow = new TencentStockQuoteAdapter(
                new TencentQuoteParser(url -> "v_sh600519=\"51~贵州茅台~600519\";"));

        assertThrows(ProviderContractException.class,
                () -> html.fetch(Collections.singletonList("600519")));
        assertThrows(ProviderContractException.class,
                () -> shortRow.fetch(Collections.singletonList("600519")));
    }

    private Quote byCode(List<Quote> quotes, String code) {
        return quotes.stream().filter(value -> code.equals(value.getInstrumentCode()))
                .findFirst().orElseThrow(AssertionError::new);
    }

    private String fixture(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/quote/" + name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalStateException("missing fixture: " + name);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
