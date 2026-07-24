package com.finscope.rpc.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 最新确认净值 Provider，在两个盘中估值端点都失败时提供可信事实。 */
@Component
public class FundNavHistoryAdapter implements QuoteAdapter {
    private static final String ENDPOINT = "https://fund.eastmoney.com/pingzhongdata/";
    private static final int HTTP_TIMEOUT_MS = 1500;
    private static final int BATCH_TIMEOUT_MS = 2500;
    private static final long MAX_CONFIRMED_NAV_AGE_DAYS = 14L;
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<MarketDataCapability> CAPABILITIES = Collections.singleton(
            MarketDataCapability.REALTIME_FUND_ESTIMATE);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FundDataRequester requester;
    private final Clock clock;
    @Resource(name = "quoteTaskExecutor")
    private Executor quoteTaskExecutor;

    public FundNavHistoryAdapter() {
        this.requester = url -> EastmoneyFundHttpClient.get(url, HTTP_TIMEOUT_MS);
        this.clock = Clock.systemDefaultZone();
    }

    FundNavHistoryAdapter(FundDataRequester requester, Executor quoteTaskExecutor) {
        this(requester, quoteTaskExecutor, Clock.systemDefaultZone());
    }

    FundNavHistoryAdapter(FundDataRequester requester, Executor quoteTaskExecutor, Clock clock) {
        this.requester = requester;
        this.quoteTaskExecutor = quoteTaskExecutor;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    @Override
    public String providerCode() { return "EASTMONEY_FUND_CONFIRMED_NAV"; }

    @Override
    public String providerFamily() { return "EASTMONEY"; }

    @Override
    public Set<MarketDataCapability> capabilities() { return CAPABILITIES; }

    @Override
    public int priority() { return 30; }

    @Override
    public int batchLimit() { return 50; }

    @Override
    public Duration minimumInterval() { return Duration.ofMillis(100); }

    @Override
    public Duration timeout() { return Duration.ofMillis(BATCH_TIMEOUT_MS); }

    @Override
    public boolean supports(String instrumentType) {
        return "FUND".equalsIgnoreCase(instrumentType);
    }

    @Override
    public boolean isTerminalFallback() { return true; }

    @Override
    public List<Quote> fetch(List<String> codes) throws Exception {
        if (codes == null || codes.isEmpty()) return Collections.emptyList();
        List<CompletableFuture<FetchOutcome>> futures = new ArrayList<CompletableFuture<FetchOutcome>>();
        for (String code : codes) {
            futures.add(CompletableFuture.supplyAsync(
                    ProviderCallDeadline.propagate(
                            () -> fetchOne(code == null ? "" : code.trim())),
                    quoteTaskExecutor));
        }
        boolean timedOut = false;
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                    .get(BATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ignored) {
            timedOut = true;
            // 已完成结果仍可使用；未完成请求在 finally 风格的收集阶段统一取消。
        }

        Map<String, FetchOutcome> byCode = new LinkedHashMap<String, FetchOutcome>();
        int successfulRequests = 0;
        Exception lastFailure = null;
        for (CompletableFuture<FetchOutcome> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
                continue;
            }
            FetchOutcome outcome;
            try {
                outcome = future.getNow(null);
            } catch (RuntimeException error) {
                lastFailure = new IOException(error.getMessage(), error);
                continue;
            }
            if (outcome == null) continue;
            byCode.put(outcome.code, outcome);
            if (outcome.requestSucceeded) successfulRequests++;
            if (outcome.failure != null) lastFailure = outcome.failure;
        }
        if (successfulRequests == 0 && (timedOut || lastFailure != null)) {
            if (lastFailure instanceof ProviderContractException) {
                throw (ProviderContractException) lastFailure;
            }
            throw new IOException("Fund history provider failed for all requested funds", lastFailure);
        }

        List<Quote> quotes = new ArrayList<Quote>();
        for (String rawCode : codes) {
            String code = rawCode == null ? "" : rawCode.trim();
            FetchOutcome outcome = byCode.get(code);
            quotes.add(outcome == null ? unavailable(code)
                    : outcome.quote);
        }
        return quotes;
    }

    private FetchOutcome fetchOne(String code) {
        Quote quote = new Quote();
        quote.setInstrumentCode(code);
        try {
            String raw = requester.get(ENDPOINT + code + ".js");
            String name = extractVariable(raw, "fS_name");
            if (name != null) quote.setName(name);
            int key = raw.indexOf("Data_netWorthTrend");
            int start = key < 0 ? -1 : raw.indexOf('[', key);
            int end = start < 0 ? -1 : raw.indexOf("];", start);
            if (start < 0 || end < start) return FetchOutcome.success(code, unavailable(quote));
            JsonNode values = objectMapper.readTree(raw.substring(start, end + 1));
            if (!values.isArray() || values.size() == 0) {
                return FetchOutcome.success(code, unavailable(quote));
            }
            JsonNode latest = values.get(values.size() - 1);
            quote.setConfirmedNav(latest.path("y").isNumber() ? latest.path("y").asDouble() : null);
            quote.setConfirmedNavChangePct(latest.path("equityReturn").isNumber()
                    ? latest.path("equityReturn").asDouble() : null);
            LocalDate confirmedDate = null;
            if (latest.path("x").isNumber()) {
                confirmedDate = Instant.ofEpochMilli(latest.path("x").asLong())
                        .atZone(MARKET_ZONE).toLocalDate();
                quote.setConfirmedNavDate(confirmedDate.toString());
            }
            if (confirmedDate != null) {
                Instant observedAt = confirmedDate.atTime(15, 0).atZone(MARKET_ZONE).toInstant();
                quote.setQuoteTime(confirmedDate.atTime(15, 0));
                quote.setAsOf(LocalDateTime.ofInstant(observedAt, clock.getZone()));
            }
            quote.setValid(validPositive(quote.getConfirmedNav())
                    && isAcceptableConfirmedDate(confirmedDate));
            quote.setNote("最新确认净值 " + quote.getConfirmedNavDate() + "；盘中估值暂不可用");
            return FetchOutcome.success(code, quote);
        } catch (Exception error) {
            return FetchOutcome.failure(code, unavailable(quote), error);
        }
    }

    private Quote unavailable(String code) {
        Quote quote = new Quote();
        quote.setInstrumentCode(code);
        return unavailable(quote);
    }

    private Quote unavailable(Quote quote) {
        quote.setValid(false);
        quote.setNote("基金确认净值获取失败");
        return quote;
    }

    private String extractVariable(String raw, String variable) {
        if (raw == null) return null;
        int key = raw.indexOf("var " + variable);
        int firstQuote = key < 0 ? -1 : raw.indexOf('"', key);
        int secondQuote = firstQuote < 0 ? -1 : raw.indexOf('"', firstQuote + 1);
        return firstQuote < 0 || secondQuote <= firstQuote
                ? null : raw.substring(firstQuote + 1, secondQuote);
    }

    private boolean validPositive(Double value) {
        return value != null && Double.isFinite(value) && value > 0.0d;
    }

    private boolean isAcceptableConfirmedDate(LocalDate date) {
        if (date == null) return false;
        LocalDate today = LocalDate.now(clock.withZone(MARKET_ZONE));
        long ageDays = java.time.temporal.ChronoUnit.DAYS.between(date, today);
        return ageDays >= 0L && ageDays <= MAX_CONFIRMED_NAV_AGE_DAYS;
    }

    private static final class FetchOutcome {
        private final String code;
        private final Quote quote;
        private final boolean requestSucceeded;
        private final Exception failure;

        private FetchOutcome(String code, Quote quote, boolean requestSucceeded, Exception failure) {
            this.code = code;
            this.quote = quote;
            this.requestSucceeded = requestSucceeded;
            this.failure = failure;
        }

        private static FetchOutcome success(String code, Quote quote) {
            return new FetchOutcome(code, quote, true, null);
        }

        private static FetchOutcome failure(String code, Quote quote, Exception failure) {
            return new FetchOutcome(code, quote, false, failure);
        }
    }
}
