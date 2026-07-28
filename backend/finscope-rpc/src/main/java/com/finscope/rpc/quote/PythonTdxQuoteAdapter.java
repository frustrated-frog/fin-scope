package com.finscope.rpc.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 通过本地 Python 市场数据服务访问通达信 TCP 行情。 */
@Component
public class PythonTdxQuoteAdapter implements QuoteAdapter {
    private static final Set<MarketDataCapability> CAPABILITIES = Collections.singleton(
            MarketDataCapability.REALTIME_STOCK_QUOTE);

    private final String baseUrl;
    private final FinanceHttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public PythonTdxQuoteAdapter(
            FinanceHttpClient http,
            @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}") String baseUrl) {
        this(baseUrl, http);
    }

    PythonTdxQuoteAdapter(String baseUrl, FinanceHttpClient http) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.http = http;
    }

    @Override
    public String providerCode() {
        return "PYTDX_QUOTE";
    }

    @Override
    public String providerFamily() {
        return "TDX";
    }

    @Override
    public Set<MarketDataCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public int priority() {
        return 25;
    }

    @Override
    public int batchLimit() {
        return 1;
    }

    @Override
    public Duration minimumInterval() {
        return Duration.ofMillis(100);
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(10);
    }

    @Override
    public boolean supports(String instrumentType) {
        return "STOCK".equalsIgnoreCase(instrumentType);
    }

    @Override
    public boolean isTerminalFallback() {
        return true;
    }

    @Override
    public List<Quote> fetch(List<String> codes) throws Exception {
        List<Quote> quotes = new ArrayList<Quote>();
        if (codes == null) return quotes;
        for (String rawCode : codes) {
            String code = normalizeCode(rawCode);
            if (code == null) continue;
            quotes.add(fetchOne(code));
        }
        return quotes;
    }

    private Quote fetchOne(String code) throws Exception {
        URI uri = URI.create(baseUrl + "/v1/stocks/" + market(code) + "/" + code
                + "/quote?provider_family=TDX&provider_mode=true");
        FinanceHttpResponse response = http.get(providerCode(), uri, Collections.<String, String>emptyMap());
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new ProviderContractException("HTTP_" + response.getStatus(),
                    "通达信行情服务返回异常状态：" + response.getStatus(), true);
        }
        JsonNode root = json.readTree(response.getBody());
        if (!"TDX".equalsIgnoreCase(text(root, "source_family"))) {
            throw new ProviderContractException("WRONG_PROVIDER_FAMILY",
                    "行情响应不是通达信独立数据源", false);
        }
        JsonNode data = root.get("data");
        Double price = decimal(data, "price");
        if (data == null || price == null || price <= 0) {
            throw new ProviderContractException("EMPTY_DATA", "通达信未返回有效行情", true);
        }
        Quote quote = new Quote();
        quote.setInstrumentCode(code);
        quote.setName(text(data, "name"));
        quote.setPrice(price);
        quote.setPreviousClose(decimal(data, "previous_close"));
        quote.setOpen(decimal(data, "open"));
        quote.setHigh(decimal(data, "high"));
        quote.setLow(decimal(data, "low"));
        quote.setChangeAmount(decimal(data, "change"));
        quote.setChangePct(decimal(data, "change_pct"));
        quote.setVolume(decimal(data, "volume"));
        quote.setTurnover(decimal(data, "amount"));
        quote.setQuoteTime(parseTime(text(data, "observed_at")));
        quote.setValid(true);
        return quote;
    }

    private static String normalizeCode(String value) {
        if (value == null) return null;
        String code = value.trim().toLowerCase(Locale.ROOT);
        if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("bj")) {
            code = code.substring(2);
        }
        return code.matches("\\d{6}") ? code : null;
    }

    private static String market(String code) {
        if (code.startsWith("6") || code.startsWith("5") || code.startsWith("9")) return "SH";
        if (code.startsWith("4") || code.startsWith("8")) return "BJ";
        return "SZ";
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value == null || value.trim().isEmpty()
                ? "http://127.0.0.1:8000" : value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Double decimal(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? null : value.doubleValue();
    }

    private static LocalDateTime parseTime(String value) {
        if (value != null) {
            try {
                return OffsetDateTime.parse(value).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDateTime.parse(value);
                } catch (DateTimeParseException ignoredAgain) {
                    // 使用接收时间兜底。
                }
            }
        }
        return LocalDateTime.now();
    }
}
