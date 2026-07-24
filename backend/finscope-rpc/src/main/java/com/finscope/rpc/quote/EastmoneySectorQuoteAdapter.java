package com.finscope.rpc.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.marketintel.DeadlineAwareHttpConnection;
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** 东方财富板块行情适配器，板块代码格式为 BK 加四位数字。 */
@Component
public class EastmoneySectorQuoteAdapter implements QuoteAdapter {
    private static final String BASE_URL = "https://push2.eastmoney.com/api/qt/stock/get?secid=90.";
    private static final String FIELDS = "&fields=f43,f44,f45,f46,f47,f48,f57,f58,f60,f170";
    private static final int TIMEOUT_MS = 8000;
    private static final Set<MarketDataCapability> CAPABILITIES = Collections.singleton(
            MarketDataCapability.REALTIME_SECTOR_QUOTE);
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Resource(name = "quoteTaskExecutor")
    private Executor quoteTaskExecutor;

    @Override
    public String providerCode() { return "EASTMONEY_SECTOR_QUOTE"; }

    @Override
    public String providerFamily() { return "EASTMONEY"; }

    @Override
    public Set<MarketDataCapability> capabilities() { return CAPABILITIES; }

    @Override
    public int priority() { return 10; }

    @Override
    public int batchLimit() { return 50; }

    @Override
    public Duration minimumInterval() { return Duration.ofMillis(200); }

    @Override
    public Duration timeout() { return Duration.ofMillis(TIMEOUT_MS); }

    @Override
    public boolean supports(String instrumentType) {
        return "SECTOR".equalsIgnoreCase(instrumentType);
    }

    @Override
    public List<Quote> fetch(List<String> codes) {
        List<Quote> quotes = new ArrayList<>();
        if (codes == null || codes.isEmpty()) {
            return quotes;
        }
        List<CompletableFuture<Quote>> futures = new ArrayList<>();
        for (String code : codes) {
            futures.add(CompletableFuture.supplyAsync(
                    ProviderCallDeadline.propagate(() -> fetchOne(code)), quoteTaskExecutor));
        }
        try {
            for (CompletableFuture<Quote> future : futures) {
                try {
                    quotes.add(future.join());
                } catch (CompletionException error) {
                    if (error.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) error.getCause();
                    }
                    throw error;
                }
            }
        } finally {
            for (CompletableFuture<Quote> future : futures) {
                if (!future.isDone()) future.cancel(true);
            }
        }
        return quotes;
    }

    private Quote fetchOne(String code) {
        Quote quote = new Quote();
        quote.setInstrumentCode(code);
        try {
            JsonNode data = objectMapper.readTree(request(BASE_URL + code.toUpperCase(Locale.ROOT) + FIELDS)).path("data");
            if (data.isMissingNode() || data.isNull()) {
                quote.setValid(false);
                quote.setNote("未取到板块行情");
                return quote;
            }
            quote.setName(data.path("f58").asText(""));
            quote.setPrice(scale(data, "f43", 1000.0));
            quote.setPreviousClose(scale(data, "f60", 1000.0));
            quote.setOpen(scale(data, "f46", 1000.0));
            quote.setHigh(scale(data, "f44", 1000.0));
            quote.setLow(scale(data, "f45", 1000.0));
            quote.setVolume(number(data, "f47"));
            quote.setTurnover(number(data, "f48"));
            quote.setChangePct(scale(data, "f170", 100.0));
            if (quote.getPrice() != null && quote.getPreviousClose() != null) {
                quote.setChangeAmount(round(quote.getPrice() - quote.getPreviousClose()));
            }
            if (quote.getPreviousClose() != null && quote.getPreviousClose() > 0
                    && quote.getHigh() != null && quote.getLow() != null) {
                quote.setAmplitude(round((quote.getHigh() - quote.getLow()) / quote.getPreviousClose() * 100));
            }
            quote.setQuoteTime(LocalDateTime.now());
            quote.setValid(quote.getPrice() != null && quote.getPrice() > 0);
            if (!quote.isValid()) {
                quote.setNote("未取到有效板块行情");
            }
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception ex) {
            quote.setValid(false);
            quote.setNote("板块行情获取失败：" + ex.getMessage());
        }
        return quote;
    }

    private Double scale(JsonNode data, String field, double divisor) {
        Double value = number(data, field);
        return value == null ? null : round(value / divisor);
    }

    private Double number(JsonNode data, String field) {
        JsonNode value = data.path(field);
        return value.isNumber() ? value.asDouble() : null;
    }

    private String request(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod("GET");
        DeadlineAwareHttpConnection.configure(connection, TIMEOUT_MS, TIMEOUT_MS, providerCode());
        connection.setRequestProperty("Referer", "https://quote.eastmoney.com");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 FinScope/0.1");
        try {
            InputStream input = DeadlineAwareHttpConnection.inputStream(
                    connection, TIMEOUT_MS, providerCode());
            return new String(DeadlineAwareHttpConnection.readAll(
                    connection, input, TIMEOUT_MS, 0, providerCode()), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
