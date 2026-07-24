package com.finscope.rpc.quote;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.marketintel.DeadlineAwareHttpConnection;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.Duration;

/**
 * 新浪财经指数行情适配器。
 * 接口：https://hq.sinajs.cn/list=s_sh000001,s_sz399001
 * 返回 GBK 文本：var hq_str_s_sh000001="名称,点位,涨跌额,涨跌幅,...";
 */
@Component
public class SinaIndexQuoteAdapter implements QuoteAdapter {
    private static final String BASE_URL = "https://hq.sinajs.cn/list=";
    private static final Charset GBK = Charset.forName("GBK");
    private static final int TIMEOUT_MS = 8000;
    private static final Set<MarketDataCapability> CAPABILITIES = Collections.singleton(
            MarketDataCapability.REALTIME_INDEX_QUOTE);

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
        for (String line : raw.split("\\n")) {
            Quote quote = parseLine(line);
            if (quote != null) {
                quotes.add(quote);
            }
        }
        return quotes;
    }

    static Quote parseLine(String line) {
        int equalsIndex = line == null ? -1 : line.indexOf('=');
        if (equalsIndex < 0) {
            return null;
        }
        Quote quote = new Quote();
        quote.setInstrumentCode(extractCode(line, equalsIndex));
        String payload = extractPayload(line, equalsIndex);
        String[] fields = payload.split(",", -1);
        if (fields.length < 4 || fields[0].trim().isEmpty()) {
            quote.setValid(false);
            quote.setNote("未取到有效行情");
            return quote;
        }
        double price = parseDouble(fields[1]);
        quote.setName(fields[0].trim());
        quote.setPrice(price);
        quote.setChangeAmount(round(parseDouble(fields[2])));
        quote.setChangePct(round(parseDouble(fields[3])));
        quote.setQuoteTime(LocalDateTime.now());
        quote.setValid(price > 0);
        if (price <= 0) {
            quote.setNote("停牌或暂无成交");
        }
        return quote;
    }

    private static String toSinaSymbol(String code) {
        String normalized = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("s_sh") || normalized.startsWith("s_sz")) {
            return normalized;
        }
        return normalized.startsWith("399") ? "s_sz" + normalized : "s_sh" + normalized;
    }

    private static String extractCode(String line, int equalsIndex) {
        int symbolIndex = line.indexOf("hq_str_s_");
        if (symbolIndex < 0) {
            return "";
        }
        String symbol = line.substring(symbolIndex + "hq_str_s_".length(), equalsIndex).trim();
        if (symbol.startsWith("sh") || symbol.startsWith("sz")) {
            return symbol.substring(2);
        }
        return symbol;
    }

    private static String extractPayload(String line, int equalsIndex) {
        int firstQuote = line.indexOf('"', equalsIndex);
        int lastQuote = line.lastIndexOf('"');
        if (firstQuote < 0 || lastQuote <= firstQuote) {
            return "";
        }
        return line.substring(firstQuote + 1, lastQuote);
    }

    private String request(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod("GET");
        DeadlineAwareHttpConnection.configure(connection, TIMEOUT_MS, TIMEOUT_MS, providerCode());
        connection.setRequestProperty("Referer", "https://finance.sina.com.cn");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 FinScope/0.1");
        try {
            InputStream input = DeadlineAwareHttpConnection.inputStream(
                    connection, TIMEOUT_MS, providerCode());
            return new String(DeadlineAwareHttpConnection.readAll(
                    connection, input, TIMEOUT_MS, 0, providerCode()), GBK);
        } finally {
            connection.disconnect();
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
