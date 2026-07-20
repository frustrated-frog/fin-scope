package com.finscope.rpc.quote;

import com.finscope.domain.instrument.Quote;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 腾讯批量行情文本的唯一网络与字段解析实现。
 */
@Component
public class TencentQuoteParser {
    private static final String ENDPOINT = "https://qt.gtimg.cn/q=";
    private static final Charset GBK = Charset.forName("GBK");
    private static final int TIMEOUT_MS = 8_000;
    private static final Pattern ROW = Pattern.compile("^v_([a-z]{2}\\d+)=\\\"(.*)\\\"$");
    private static final DateTimeFormatter TENCENT_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final Fetcher fetcher;

    public TencentQuoteParser() {
        this(TencentQuoteParser::requestGbk);
    }

    TencentQuoteParser(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    private static String requestGbk(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("Referer", "https://gu.qq.com");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 FinScope/0.1");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new ProviderContractException("HTTP_" + status,
                        "Tencent quote returned HTTP " + status,
                        status == 429 || status == 502 || status == 503 || status == 504);
            }
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
                return new String(output.toByteArray(), GBK);
            }
        } finally {
            connection.disconnect();
        }
    }

    public List<Quote> fetch(List<String> symbols) throws Exception {
        Set<String> requested = new LinkedHashSet<String>();
        for (String symbol : symbols) {
            if (symbol != null && !symbol.trim().isEmpty()) {
                requested.add(symbol.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (requested.isEmpty()) return new ArrayList<Quote>();
        String raw = fetcher.get(ENDPOINT + String.join(",", requested));
        if (raw == null || raw.trim().isEmpty() || raw.toLowerCase(Locale.ROOT).contains("<html")) {
            throw new ProviderContractException("INVALID_RESPONSE", "腾讯行情返回了空内容或 HTML", true);
        }

        Map<String, Quote> parsed = new LinkedHashMap<String, Quote>();
        ProviderContractException firstError = null;
        for (String fragment : raw.split(";")) {
            String line = fragment.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher matcher = ROW.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String symbol = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!requested.contains(symbol)) {
                continue;
            }
            try {
                parsed.put(symbol, parse(symbol, matcher.group(2)));
            } catch (ProviderContractException error) {
                if (firstError == null) {
                    firstError = error;
                }
            }
        }
        if (parsed.isEmpty()) {
            if (firstError != null) throw firstError;
            throw new ProviderContractException("EMPTY_RESPONSE", "腾讯行情未返回请求的标的", true);
        }
        List<Quote> ordered = new ArrayList<Quote>();
        for (String symbol : requested) {
            Quote quote = parsed.get(symbol);
            if (quote != null) ordered.add(quote);
        }
        return ordered;
    }

    Quote parse(String symbol, String payload) {
        String[] values = payload.split("~", -1);
        if (values.length < 53) {
            throw new ProviderContractException("SCHEMA_DRIFT", "腾讯行情字段不足", true);
        }
        Quote quote = new Quote();
        quote.setInstrumentCode(symbol.substring(2));
        quote.setName(values[1].trim());
        quote.setPrice(number(values[3]));
        quote.setPreviousClose(number(values[4]));
        quote.setOpen(number(values[5]));
        quote.setChangeAmount(number(values[31]));
        quote.setChangePct(number(values[32]));
        quote.setHigh(number(values[33]));
        quote.setLow(number(values[34]));
        quote.setVolume(scale(number(values[36]), 100.0d));
        quote.setTurnover(scale(number(values[37]), 10_000.0d));
        quote.setAmplitude(number(values[43]));
        quote.setAsOf(parseTencentTime(values[30]));
        quote.setQuoteTime(quote.getAsOf());
        quote.setValid(!quote.getName().isEmpty() && quote.getPrice() != null && quote.getPrice() > 0.0d);
        if (!quote.isValid()) quote.setNote("腾讯行情暂无有效成交");
        return quote;
    }

    private Double number(String value) {
        try {
            if (value == null || value.trim().isEmpty() || "--".equals(value.trim())) return null;
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private Double scale(Double value, double multiplier) {
        return value == null ? null : value * multiplier;
    }

    private LocalDateTime parseTencentTime(String value) {
        try {
            return value == null || value.trim().isEmpty()
                    ? null : LocalDateTime.parse(value.trim(), TENCENT_TIME);
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    @FunctionalInterface
    interface Fetcher {
        String get(String url) throws Exception;
    }
}
