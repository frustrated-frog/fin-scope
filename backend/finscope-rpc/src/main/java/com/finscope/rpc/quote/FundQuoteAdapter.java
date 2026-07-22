package com.finscope.rpc.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataCapability;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 天天基金 FundValuationLast 批量盘中估值主 Provider。 */
@Component
public class FundQuoteAdapter implements QuoteAdapter {
    static final String PRIMARY_ENDPOINT =
            "https://fundcomapi.tiantianfunds.com/mm/newCore/FundValuationLast";
    static final String FIELDS =
            "FCODE,SHORTNAME,GSZZL,GZTIME,GSZ,NAV,PDATE,NAVCHGRT";
    private static final int TIMEOUT_MS = 8000;
    private static final DateTimeFormatter ESTIMATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<MarketDataCapability> CAPABILITIES = Collections.singleton(
            MarketDataCapability.REALTIME_FUND_ESTIMATE);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String endpoint;
    private final String providerCode;
    private final int priority;
    private final FundDataRequester requester;

    public FundQuoteAdapter() {
        this(PRIMARY_ENDPOINT, "EASTMONEY_FUND_VALUATION", 10, null);
    }

    protected FundQuoteAdapter(String endpoint, String providerCode, int priority) {
        this(endpoint, providerCode, priority, null);
    }

    FundQuoteAdapter(FundDataRequester requester) {
        this(PRIMARY_ENDPOINT, "EASTMONEY_FUND_VALUATION", 10, requester);
    }

    FundQuoteAdapter(String endpoint, String providerCode, int priority,
                     FundDataRequester requester) {
        this.endpoint = endpoint;
        this.providerCode = providerCode;
        this.priority = priority;
        this.requester = requester == null ? this::request : requester;
    }

    @Override
    public String providerCode() { return providerCode; }

    @Override
    public String providerFamily() { return "EASTMONEY"; }

    @Override
    public Set<MarketDataCapability> capabilities() { return CAPABILITIES; }

    @Override
    public int priority() { return priority; }

    @Override
    public int batchLimit() { return 50; }

    @Override
    public Duration minimumInterval() { return Duration.ofMillis(100); }

    @Override
    public Duration timeout() { return Duration.ofMillis(TIMEOUT_MS); }

    @Override
    public boolean supports(String instrumentType) {
        return "FUND".equalsIgnoreCase(instrumentType);
    }

    @Override
    public List<Quote> fetch(List<String> codes) throws Exception {
        List<String> requested = normalizeCodes(codes);
        if (requested.isEmpty()) return Collections.emptyList();

        JsonNode root = objectMapper.readTree(requester.get(buildUrl(requested)));
        JsonNode data = root.path("data");
        if (!root.path("success").asBoolean(false) || !data.isArray()) {
            throw new IOException("FundValuationLast returned an invalid payload");
        }

        Map<String, Quote> byCode = new LinkedHashMap<String, Quote>();
        for (JsonNode item : data) {
            Quote quote = parseQuote(item);
            if (quote.getInstrumentCode() != null) {
                byCode.put(quote.getInstrumentCode(), quote);
            }
        }

        List<Quote> result = new ArrayList<Quote>();
        for (String code : requested) {
            Quote quote = byCode.get(code);
            result.add(quote == null ? unavailable(code) : quote);
        }
        return result;
    }

    private Quote parseQuote(JsonNode item) {
        Quote quote = new Quote();
        quote.setInstrumentCode(text(item, "FCODE").toUpperCase(Locale.ROOT));
        quote.setName(text(item, "SHORTNAME"));
        quote.setConfirmedNav(number(item, "NAV"));
        quote.setConfirmedNavChangePct(number(item, "NAVCHGRT"));
        quote.setConfirmedNavDate(text(item, "PDATE"));
        quote.setPrice(number(item, "GSZ"));
        quote.setChangePct(number(item, "GSZZL"));
        String estimateAt = text(item, "GZTIME");
        LocalDateTime parsedEstimateAt = parseEstimateTime(estimateAt);
        quote.setQuoteTime(parsedEstimateAt == null ? LocalDateTime.now() : parsedEstimateAt);
        boolean estimateAvailable = validPositive(quote.getPrice());
        quote.setValid(estimateAvailable || validPositive(quote.getConfirmedNav()));
        quote.setNote(estimateAvailable
                ? "盘中估值 " + estimateAt
                : "最新确认净值 " + quote.getConfirmedNavDate() + "；盘中估值暂未提供");
        return quote;
    }

    private String buildUrl(List<String> codes) throws Exception {
        return endpoint + "?FCODES="
                + URLEncoder.encode(String.join(",", codes), StandardCharsets.UTF_8.name())
                + "&FIELDS=" + URLEncoder.encode(FIELDS, StandardCharsets.UTF_8.name());
    }

    private List<String> normalizeCodes(List<String> codes) {
        if (codes == null) return Collections.emptyList();
        List<String> normalized = new ArrayList<String>();
        for (String code : codes) {
            if (code != null && !code.trim().isEmpty()) {
                normalized.add(code.trim().toUpperCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private Quote unavailable(String code) {
        Quote quote = new Quote();
        quote.setInstrumentCode(code);
        quote.setValid(false);
        quote.setNote("基金估值接口未返回该基金");
        return quote;
    }

    private String text(JsonNode item, String field) {
        JsonNode value = item.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private Double number(JsonNode item, String field) {
        JsonNode value = item.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (value.isNumber()) return value.asDouble();
        try {
            String text = value.asText("").trim();
            return text.isEmpty() || "--".equals(text) ? null : Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private LocalDateTime parseEstimateTime(String value) {
        try {
            return value == null || value.isEmpty() ? null
                    : LocalDateTime.parse(value, ESTIMATE_TIME);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean validPositive(Double value) {
        return value != null && Double.isFinite(value) && value > 0.0d;
    }

    private String request(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("Referer", "https://fund.eastmoney.com");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 FinScope/0.1");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IOException("FundValuationLast HTTP " + status);
        }
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) buffer.write(chunk, 0, read);
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }
}
