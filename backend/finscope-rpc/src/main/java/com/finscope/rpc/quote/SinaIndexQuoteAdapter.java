package com.finscope.rpc.quote;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataCapability;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.Duration;

/**
 * 新浪财经指数行情适配器。
 * 使用包含来源时间的完整行情响应；简版 s_ 响应没有时间戳，不能用于新鲜度判断。
 */
@Component
public class SinaIndexQuoteAdapter implements QuoteAdapter {
    private static final String BASE_URL = "https://hq.sinajs.cn/list=";
    private static final Charset GBK = Charset.forName("GBK");
    private static final int TIMEOUT_MS = 8000;
    private static final Set<MarketDataCapability> CAPABILITIES = Collections.singleton(
            MarketDataCapability.REALTIME_INDEX_QUOTE);
    @Autowired
    private QuoteHttpTransport httpTransport;

    @Override
    public String providerCode() { return "SINA_INDEX"; }

    @Override
    public String providerFamily() { return "SINA"; }

    @Override
    public Set<MarketDataCapability> capabilities() { return CAPABILITIES; }

    @Override
    public int priority() { return 20; }

    @Override
    public int batchLimit() { return 100; }

    @Override
    public Duration minimumInterval() { return Duration.ofMillis(200); }

    @Override
    public Duration timeout() { return Duration.ofMillis(TIMEOUT_MS); }

    @Override
    public boolean supports(String instrumentType) {
        return "INDEX".equalsIgnoreCase(instrumentType);
    }

    @Override
    public List<Quote> fetch(List<String> codes) throws Exception {
        List<Quote> quotes = new ArrayList<Quote>();
        if (codes == null || codes.isEmpty()) {
            return quotes;
        }
        StringBuilder symbols = new StringBuilder();
        for (String code : codes) {
            if (symbols.length() > 0) {
                symbols.append(',');
            }
            symbols.append(toSinaSymbol(code));
        }
        String raw = request(BASE_URL + symbols);
        for (String line : raw.split("\n")) {
            Quote quote = parseLine(line);
            if (quote != null) {
                quotes.add(quote);
            }
        }
        return quotes;
    }

    static Quote parseLine(String line) {
        return SinaStockQuoteAdapter.parseLine(line);
    }

    private static String toSinaSymbol(String code) {
        String normalized = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("s_")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("sh") || normalized.startsWith("sz")) {
            return normalized;
        }
        return normalized.startsWith("399") ? "sz" + normalized : "sh" + normalized;
    }

    private String request(String urlText) throws Exception {
        return httpTransport.get(providerCode(), URI.create(urlText), TIMEOUT_MS,
                2 * 1024 * 1024,
                Collections.singletonMap("Referer", "https://finance.sina.com.cn"), GBK);
    }

}
