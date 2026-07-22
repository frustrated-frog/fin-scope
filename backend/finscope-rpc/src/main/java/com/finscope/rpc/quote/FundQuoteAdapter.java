package com.finscope.rpc.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataCapability;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
    private static final int TIMEOUT_MS = 2500;
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration MAX_ESTIMATE_AGE = Duration.ofHours(16);
    private static final long MAX_CONFIRMED_NAV_AGE_DAYS = 14L;
    private static final DateTimeFormatter ESTIMATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<MarketDataCapability> CAPABILITIES = Collections.singleton(
            MarketDataCapability.REALTIME_FUND_ESTIMATE);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String endpoint;
    private final String providerCode;
    private final int priority;
    private final FundDataRequester requester;
    private final Clock clock;

    public FundQuoteAdapter() {
        this(PRIMARY_ENDPOINT, "EASTMONEY_FUND_VALUATION", 10, null,
                Clock.systemDefaultZone());
    }

    protected FundQuoteAdapter(String endpoint, String providerCode, int priority) {
        this(endpoint, providerCode, priority, null, Clock.systemDefaultZone());
    }

    FundQuoteAdapter(FundDataRequester requester) {
        this(PRIMARY_ENDPOINT, "EASTMONEY_FUND_VALUATION", 10, requester,
                Clock.systemDefaultZone());
    }

    FundQuoteAdapter(FundDataRequester requester, Clock clock) {
        this(PRIMARY_ENDPOINT, "EASTMONEY_FUND_VALUATION", 10, requester, clock);
    }

    FundQuoteAdapter(String endpoint, String providerCode, int priority,
                     FundDataRequester requester) {
        this(endpoint, providerCode, priority, requester, Clock.systemDefaultZone());
    }

    FundQuoteAdapter(String endpoint, String providerCode, int priority,
                     FundDataRequester requester, Clock clock) {
        this.endpoint = endpoint;
        this.providerCode = providerCode;
        this.priority = priority;
        this.requester = requester == null
                ? url -> EastmoneyFundHttpClient.get(url, TIMEOUT_MS) : requester;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
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
        LocalDate confirmedDate = parseConfirmedDate(quote.getConfirmedNavDate());
        boolean confirmedAvailable = validPositive(quote.getConfirmedNav())
                && isAcceptableConfirmedDate(confirmedDate);
        String estimateAt = text(item, "GZTIME");
        ZonedDateTime parsedEstimateAt = parseEstimateTime(estimateAt);
        boolean estimateAvailable = confirmedAvailable
                && validPositive(number(item, "GSZ"))
                && isCurrentEstimate(parsedEstimateAt);
        if (estimateAvailable) {
            quote.setPrice(number(item, "GSZ"));
            quote.setChangePct(number(item, "GSZZL"));
            quote.setQuoteTime(parsedEstimateAt.toLocalDateTime());
            quote.setAsOf(toClockTime(parsedEstimateAt.toInstant()));
        } else {
            quote.setPrice(null);
            quote.setChangePct(null);
            quote.setQuoteTime(confirmedDate == null ? null : confirmedDate.atTime(15, 0));
            quote.setAsOf(confirmedDate == null ? null
                    : toClockTime(confirmedDate.atTime(15, 0).atZone(MARKET_ZONE).toInstant()));
        }
        quote.setValid(confirmedAvailable);
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

    private ZonedDateTime parseEstimateTime(String value) {
        try {
            return value == null || value.isEmpty() ? null
                    : LocalDateTime.parse(value, ESTIMATE_TIME).atZone(MARKET_ZONE);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private LocalDate parseConfirmedDate(String value) {
        try {
            return value == null || value.isEmpty() ? null : LocalDate.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isCurrentEstimate(ZonedDateTime estimateAt) {
        if (estimateAt == null) return false;
        Instant now = clock.instant();
        Duration age = Duration.between(estimateAt.toInstant(), now);
        return estimateAt.toLocalDate().equals(LocalDate.now(clock.withZone(MARKET_ZONE)))
                && !estimateAt.toInstant().isAfter(now.plus(Duration.ofMinutes(2)))
                && !age.isNegative() && age.compareTo(MAX_ESTIMATE_AGE) <= 0;
    }

    private boolean isAcceptableConfirmedDate(LocalDate date) {
        if (date == null) return false;
        LocalDate today = LocalDate.now(clock.withZone(MARKET_ZONE));
        long ageDays = java.time.temporal.ChronoUnit.DAYS.between(date, today);
        return ageDays >= 0L && ageDays <= MAX_CONFIRMED_NAV_AGE_DAYS;
    }

    private LocalDateTime toClockTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, clock.getZone());
    }

    private boolean validPositive(Double value) {
        return value != null && Double.isFinite(value) && value > 0.0d;
    }

}
