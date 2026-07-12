package com.finscope.rpc.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Quote;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 天天基金实时估值适配器。
 * 接口：https://fundgz.1234567.com.cn/js/000001.js
 * 返回 JSONP：jsonpgz({"fundcode":"000001","name":"...","gsz":"1.234","gszzl":"-1.23","gztime":"..."});
 * gsz=估算净值，gszzl=估算涨跌幅，gztime=估值时间。
 */
@Component
public class FundQuoteAdapter implements QuoteAdapter {
    private static final String BASE_URL = "https://fundgz.1234567.com.cn/js/";
    private static final String NAV_HISTORY_URL = "https://fund.eastmoney.com/pingzhongdata/";
    private static final int TIMEOUT_MS = 8000;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Resource(name = "quoteTaskExecutor")
    private Executor quoteTaskExecutor;

    @Override
    public boolean supports(String instrumentType) {
        return "FUND".equalsIgnoreCase(instrumentType);
    }

    @Override
    public List<Quote> fetch(List<String> codes) throws Exception {
        List<Quote> quotes = new ArrayList<>();
        if (codes == null || codes.isEmpty()) {
            return quotes;
        }
        List<CompletableFuture<Quote>> futures = new ArrayList<CompletableFuture<Quote>>();
        for (String code : codes) {
            futures.add(CompletableFuture.supplyAsync(() -> fetchOne(code.trim()), quoteTaskExecutor));
        }
        for (CompletableFuture<Quote> future : futures) {
            quotes.add(future.join());
        }
        return quotes;
    }

    private Quote fetchOne(String code) {
        Quote quote = new Quote();
        quote.setInstrumentCode(code);
        try {
            String raw = request(BASE_URL + code + ".js");
            String json = extractJson(raw);
            if (json == null) {
                quote.setValid(false);
                quote.setNote("未取到基金估值");
                return quote;
            }
            JsonNode node = objectMapper.readTree(json);
            quote.setName(node.path("name").asText(""));
            quote.setConfirmedNav(parseDouble(node.path("dwjz").asText("")));
            quote.setConfirmedNavDate(node.path("jzrq").asText(""));
            enrichLatestConfirmedNav(quote, code);
            quote.setPrice(parseDouble(node.path("gsz").asText("")));
            quote.setChangePct(parseDouble(node.path("gszzl").asText("")));
            quote.setQuoteTime(LocalDateTime.now());
            quote.setNote("盘中估值 " + node.path("gztime").asText(""));
            quote.setValid(quote.getPrice() != null && quote.getPrice() > 0);
        } catch (Exception ex) {
            quote.setValid(false);
            quote.setNote("基金估值获取失败：" + ex.getMessage());
        }
        return quote;
    }

    private void enrichLatestConfirmedNav(Quote quote, String code) {
        try {
            String raw = request(NAV_HISTORY_URL + code + ".js");
            int key = raw.indexOf("Data_netWorthTrend");
            int start = key < 0 ? -1 : raw.indexOf('[', key);
            int end = start < 0 ? -1 : raw.indexOf("];", start);
            if (start < 0 || end < start) return;
            JsonNode values = objectMapper.readTree(raw.substring(start, end + 1));
            if (!values.isArray() || values.size() == 0) return;
            JsonNode latest = values.get(values.size() - 1);
            quote.setConfirmedNav(latest.path("y").isNumber() ? latest.path("y").asDouble() : quote.getConfirmedNav());
            quote.setConfirmedNavChangePct(latest.path("equityReturn").isNumber() ? latest.path("equityReturn").asDouble() : null);
            if (latest.path("x").isNumber()) quote.setConfirmedNavDate(Instant.ofEpochMilli(latest.path("x").asLong()).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString());
        } catch (Exception ignored) {
            // Keep the lightweight estimate endpoint's last confirmed NAV as a fallback.
        }
    }

    private String extractJson(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
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
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
