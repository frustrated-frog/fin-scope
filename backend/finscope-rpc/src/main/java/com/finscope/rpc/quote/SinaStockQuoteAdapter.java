package com.finscope.rpc.quote;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataCapability;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
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
 * 新浪财经股票行情适配器。
 * 接口：https://hq.sinajs.cn/list=sh600519,sz000001
 * 返回 GBK 文本：var hq_str_sh600519="名称,今开,昨收,现价,最高,最低,...,日期,时间";
 * 需要带 Referer 头，否则返回 403。
 */
@Component
public class SinaStockQuoteAdapter implements QuoteAdapter {
    private static final String BASE_URL = "https://hq.sinajs.cn/list=";
    private static final Charset GBK = Charset.forName("GBK");
    private static final int TIMEOUT_MS = 8000;
    private static final Set<MarketDataCapability> CAPABILITIES = Collections.singleton(
            MarketDataCapability.REALTIME_STOCK_QUOTE);

    @Override
    public String providerCode() {
        return "SINA_STOCK";
    }

    @Override
    public String providerFamily() {
        return "SINA";
    }

    @Override
    public Set<MarketDataCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public int batchLimit() {
        return 100;
    }

    @Override
    public Duration minimumInterval() {
        return Duration.ofMillis(200);
    }

    @Override
    public Duration timeout() {
        return Duration.ofMillis(TIMEOUT_MS);
    }

    @Override
    public boolean supports(String instrumentType) {
        return "STOCK".equalsIgnoreCase(instrumentType);
    }

    @Override
    public List<Quote> fetch(List<String> codes) throws Exception {
        List<Quote> quotes = new ArrayList<>();
        if (codes == null || codes.isEmpty()) {
            return quotes;
        }
        StringBuilder param = new StringBuilder();
        for (String code : codes) {
            if (param.length() > 0) {
                param.append(",");
            }
            param.append(toSinaSymbol(code));
        }
        String raw = request(BASE_URL + param);
        for (String line : raw.split("\n")) {
            Quote quote = parseLine(line);
            if (quote != null) {
                quotes.add(quote);
            }
        }
        return quotes;
    }

    /**
     * 600519 -> sh600519；000001 -> sz000001
     */
    private String toSinaSymbol(String code) {
        String c = code.trim().toLowerCase(Locale.ROOT);
        if (c.startsWith("sh") || c.startsWith("sz") || c.startsWith("bj")) {
            return c;
        }
        if (c.startsWith("6")) {
            return "sh" + c;
        }
        if (c.startsWith("0") || c.startsWith("3")) {
            return "sz" + c;
        }
        if (c.startsWith("4") || c.startsWith("8")) {
            return "bj" + c;
        }
        return "sh" + c;
    }

    private Quote parseLine(String line) {
        int eq = line.indexOf('=');
        if (eq < 0) {
            return null;
        }
        String symbol = extractSymbol(line, eq);
        int firstQuote = line.indexOf('"', eq);
        int lastQuote = line.lastIndexOf('"');
        if (firstQuote < 0 || lastQuote <= firstQuote) {
            return null;
        }
        String payload = line.substring(firstQuote + 1, lastQuote);
        String[] fields = payload.split(",");
        Quote quote = new Quote();
        quote.setInstrumentCode(stripPrefix(symbol));
        if (fields.length < 4 || payload.trim().isEmpty()) {
            quote.setValid(false);
            quote.setNote("未取到有效行情");
            return quote;
        }
        quote.setName(fields[0]);
        double previousClose = parseDouble(fields[2]);
        double price = parseDouble(fields[3]);
        quote.setPreviousClose(previousClose);
        quote.setPrice(price);
        if (previousClose > 0) {
            double change = price - previousClose;
            quote.setChangeAmount(round(change));
            quote.setChangePct(round(change / previousClose * 100.0));
        }
        double open = parseDouble(fields[1]);
        if (open > 0) {
            quote.setOpen(round(open));
        }
        if (fields.length > 5) {
            double high = parseDouble(fields[4]);
            double low = parseDouble(fields[5]);
            if (high > 0) {
                quote.setHigh(round(high));
            }
            if (low > 0) {
                quote.setLow(round(low));
            }
            if (previousClose > 0 && high > 0 && low > 0) {
                quote.setAmplitude(round((high - low) / previousClose * 100.0));
            }
        }
        if (fields.length > 9) {
            quote.setVolume(parseDouble(fields[8]));
            quote.setTurnover(parseDouble(fields[9]));
        }
        quote.setQuoteTime(LocalDateTime.now());
        // 现价为 0 通常代表停牌或非交易时段无成交
        quote.setValid(price > 0);
        if (price <= 0) {
            quote.setNote("停牌或暂无成交");
        }
        return quote;
    }

    private String extractSymbol(String line, int eq) {
        int idx = line.indexOf("hq_str_");
        if (idx < 0) {
            return "";
        }
        return line.substring(idx + "hq_str_".length(), eq).trim();
    }

    private String stripPrefix(String symbol) {
        if (symbol.length() > 2
                && (symbol.startsWith("sh") || symbol.startsWith("sz") || symbol.startsWith("bj"))) {
            return symbol.substring(2);
        }
        return symbol;
    }

    private String request(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("Referer", "https://finance.sina.com.cn");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 FinScope/0.1");
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), GBK);
        } finally {
            connection.disconnect();
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
