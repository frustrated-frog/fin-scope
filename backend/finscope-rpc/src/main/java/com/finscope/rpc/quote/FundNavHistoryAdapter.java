package com.finscope.rpc.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataCapability;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** 最新确认净值 Provider，在两个盘中估值端点都失败时提供可信事实。 */
@Component
public class FundNavHistoryAdapter implements QuoteAdapter {
    private static final String ENDPOINT = "https://fund.eastmoney.com/pingzhongdata/";
    private static final int TIMEOUT_MS = 8000;
    private static final Set<MarketDataCapability> CAPABILITIES = Collections.singleton(
            MarketDataCapability.REALTIME_FUND_ESTIMATE);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FundDataRequester requester;
    @Resource(name = "quoteTaskExecutor")
    private Executor quoteTaskExecutor;

    public FundNavHistoryAdapter() {
        this.requester = this::request;
    }

    FundNavHistoryAdapter(FundDataRequester requester, Executor quoteTaskExecutor) {
        this.requester = requester;
        this.quoteTaskExecutor = quoteTaskExecutor;
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
    public Duration timeout() { return Duration.ofMillis(TIMEOUT_MS); }

    @Override
    public boolean supports(String instrumentType) {
        return "FUND".equalsIgnoreCase(instrumentType);
    }

    @Override
    public List<Quote> fetch(List<String> codes) {
        if (codes == null || codes.isEmpty()) return Collections.emptyList();
        List<CompletableFuture<Quote>> futures = new ArrayList<CompletableFuture<Quote>>();
        for (String code : codes) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> fetchOne(code == null ? "" : code.trim()), quoteTaskExecutor));
        }
        List<Quote> quotes = new ArrayList<Quote>();
        for (CompletableFuture<Quote> future : futures) quotes.add(future.join());
        return quotes;
    }

    private Quote fetchOne(String code) {
        Quote quote = new Quote();
        quote.setInstrumentCode(code);
        try {
            String raw = requester.get(ENDPOINT + code + ".js");
            String name = extractVariable(raw, "fS_name");
            if (name != null) quote.setName(name);
            int key = raw.indexOf("Data_netWorthTrend");
            int start = key < 0 ? -1 : raw.indexOf('[', key);
            int end = start < 0 ? -1 : raw.indexOf("];", start);
            if (start < 0 || end < start) return unavailable(quote);
            JsonNode values = objectMapper.readTree(raw.substring(start, end + 1));
            if (!values.isArray() || values.size() == 0) return unavailable(quote);
            JsonNode latest = values.get(values.size() - 1);
            quote.setConfirmedNav(latest.path("y").isNumber() ? latest.path("y").asDouble() : null);
            quote.setConfirmedNavChangePct(latest.path("equityReturn").isNumber()
                    ? latest.path("equityReturn").asDouble() : null);
            if (latest.path("x").isNumber()) {
                quote.setConfirmedNavDate(Instant.ofEpochMilli(latest.path("x").asLong())
                        .atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString());
            }
            quote.setQuoteTime(LocalDateTime.now());
            quote.setValid(validPositive(quote.getConfirmedNav()));
            quote.setNote("最新确认净值 " + quote.getConfirmedNavDate() + "；盘中估值暂不可用");
            return quote;
        } catch (Exception ignored) {
            return unavailable(quote);
        }
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

    private String request(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("Referer", "https://fund.eastmoney.com");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 FinScope/0.1");
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
