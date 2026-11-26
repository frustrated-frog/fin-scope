package com.finscope.rpc.globalmarket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.globalmarket.GlobalMarketSeries;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.quote.QuoteHttpTransport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.TreeMap;

@Component
public class GlobalMarketSource {
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final Map<String, String> INDEXES = Map.of("SPX", "^GSPC", "DJI", "^DJI", "IXIC", "^IXIC");
    private static final Map<String, String> SINA_HEADERS = Map.of("Referer", "https://finance.sina.com.cn/");

    @Autowired
    private QuoteHttpTransport transport;
    @Autowired
    private ObjectMapper json;
    @Value("${finscope.global-market.tencent-url}")
    private String tencentUrl;
    @Value("${finscope.global-market.chart-url}")
    private String chartUrl;
    @Value("${finscope.global-market.coinbase-url}")
    private String coinbaseUrl;
    @Value("${finscope.global-market.gold-history-url}")
    private String goldHistoryUrl;
    @Value("${finscope.global-market.gold-quote-url}")
    private String goldQuoteUrl;

    public GlobalMarketSeries fetch(String id) throws IOException {
        if ("XAU".equals(id)) {
            return gold();
        }
        if ("BTC".equals(id) || "ETH".equals(id)) {
            return crypto(id);
        }
        try {
            return tencent(id);
        } catch (ProviderContractException | IOException error) {
            return usMarket(id);
        }
    }

    private JsonNode read(String source, String url, Map<String, String> headers) throws IOException {
        return json.readTree(transport.get(source, URI.create(url), 8000, 2_000_000, headers, StandardCharsets.UTF_8));
    }

    private JsonNode tencentSnapshot(String key) throws IOException {
        return read("TENCENT_GLOBAL", tencentUrl + key + ",day,,,250,qfq", Map.of())
                .path("data").path(key);
    }

    private GlobalMarketSeries tencent(String id) throws IOException {
        boolean index = INDEXES.containsKey(id);
        String code = index ? Map.of("SPX", ".INX", "DJI", ".DJI", "IXIC", ".IXIC").get(id)
                : id.substring(3).replace('-', '.');
        String key = "us" + code;
        JsonNode data = tencentSnapshot(key);
        JsonNode quote = data.path("qt").path(key);
        String listing = quote.path(2).asText();
        if (!index && listing.startsWith(code + ".") && listing.matches("[A-Z.]{1,14}")) {
            key = "us" + listing;
            data = tencentSnapshot(key);
            quote = data.path("qt").path(key);
        }
        String actual = quote.path(2).asText();
        if (!(actual.equals(code) || (!index && actual.equals(listing) && actual.startsWith(code + ".")))
                || !"USD".equals(quote.path(35).asText()) || !positive(quote.path(3))) {
            throw new IOException("缺少对应的美股报价");
        }
        LocalDateTime local = LocalDateTime.parse(quote.get(30).asText().replace(' ', 'T'));
        String status = "常规盘报价";
        for (String item : data.at("/qt/market/0").asText().split("\\|")) {
            if (item.startsWith("US_")) {
                String[] parts = item.split("_", 3);
                if (parts.length == 3 && parts[2].length() <= 20) {
                    status = parts[2];
                }
            }
        }
        boolean adjusted = data.has("qfqday");
        TreeMap<String, Double> history = new TreeMap<>();
        for (JsonNode bar : data.path(adjusted ? "qfqday" : "day")) {
            LocalDate day = LocalDate.parse(bar.path(0).asText());
            if (positive(bar.path(2)) && (day.isBefore(local.toLocalDate())
                    || (day.equals(local.toLocalDate()) && local.getHour() >= 16))) {
                history.put(day.toString(), bar.path(2).asDouble());
            }
        }
        return series(code, adjusted ? "腾讯行情 · 前复权日线" : "腾讯行情", status, local.atZone(NEW_YORK).toInstant(),
                quote.get(3).asDouble(), quote.path(4).asDouble(), history);
    }

    private GlobalMarketSeries usMarket(String id) throws IOException {
        boolean index = INDEXES.containsKey(id);
        String symbol = index ? INDEXES.get(id) : id.substring("US-".length());
        JsonNode result = read("YAHOO_CHART", chartUrl + symbol.replace("^", "%5E")
                + "?interval=1d&range=1y", Map.of()).at("/chart/result/0");
        JsonNode meta = result.path("meta");
        if (!symbol.equals(meta.path("symbol").asText()) || !"USD".equals(meta.path("currency").asText())
                || !(index ? "INDEX" : "EQUITY").equals(meta.path("instrumentType").asText())
                || !NEW_YORK.getId().equals(meta.path("exchangeTimezoneName").asText())) {
            throw new IOException("未取得对应的美元美股行情");
        }
        Instant at = time(meta.path("regularMarketTime"));
        LocalDate quoteDay = at.atZone(NEW_YORK).toLocalDate();
        JsonNode prices = result.at("/indicators/quote/0/close");
        JsonNode stamps = result.path("timestamp");
        TreeMap<String, Double> history = new TreeMap<>();
        long sessionEnd = meta.at("/currentTradingPeriod/regular/end").asLong();
        for (int i = 0; i < stamps.size(); i++) {
            LocalDate day = time(stamps.path(i)).atZone(NEW_YORK).toLocalDate();
            if (day.isAfter(quoteDay) || !positive(prices.path(i))) {
                continue;
            }
            if (day.equals(quoteDay) && (sessionEnd <= 0 || at.getEpochSecond() < sessionEnd)) {
                continue;
            }
            history.put(day.toString(), prices.path(i).asDouble());
        }
        Map.Entry<String, Double> prior = history.lowerEntry(quoteDay.toString());
        if (prior == null) {
            throw new IOException("缺少前一交易日收盘价");
        }
        return series(symbol, "Yahoo Finance", "常规盘报价", at, meta.path("regularMarketPrice").asDouble(), prior.getValue(), history);
    }

    private GlobalMarketSeries gold() throws IOException {
        String raw = transport.get("SINA_XAU", URI.create(goldQuoteUrl), 8000, 8192, SINA_HEADERS, Charset.forName("GBK")).strip();
        String prefix = "var hq_str_hf_XAU=\"";
        if (!raw.startsWith(prefix) || !raw.endsWith("\";")) {
            throw new IOException("现货黄金报价格式异常");
        }
        String[] fields = raw.substring(prefix.length(), raw.length() - 2).split(",", -1);
        if (fields.length < 13) {
            throw new IOException("缺少黄金报价时间");
        }
        LocalDateTime local = LocalDateTime.parse(fields[12] + "T" + fields[6]);
        TreeMap<String, Double> history = new TreeMap<>();
        for (JsonNode bar : read("SINA_XAU", goldHistoryUrl, SINA_HEADERS)) {
            LocalDate day = LocalDate.parse(bar.path("date").asText());
            if (day.isBefore(local.toLocalDate()) && positive(bar.path("close"))) {
                history.put(day.toString(), bar.path("close").asDouble());
            }
        }
        return series("XAU/USD", "新浪财经 · 伦敦金", "现货报价", local.atZone(BEIJING).toInstant(),
                Double.parseDouble(fields[0]), Double.parseDouble(fields[7]), history);
    }

    private GlobalMarketSeries crypto(String id) throws IOException {
        String product = id + "-USD";
        String base = coinbaseUrl + "/products/" + product;
        JsonNode ticker = read("COINBASE", base + "/ticker", Map.of());
        Instant at = Instant.parse(ticker.path("time").asText());
        JsonNode stats = read("COINBASE", base + "/stats", Map.of());
        TreeMap<String, Double> history = new TreeMap<>();
        for (JsonNode bar : read("COINBASE", base + "/candles?granularity=86400", Map.of())) {
            LocalDate day = time(bar.path(0)).atZone(ZoneOffset.UTC).toLocalDate();
            if (day.isBefore(at.atZone(ZoneOffset.UTC).toLocalDate()) && positive(bar.path(4))) {
                history.put(day.toString(), bar.get(4).asDouble());
            }
        }
        return series(product, "Coinbase Exchange", "全天交易", at, ticker.path("price").asDouble(), stats.path("open").asDouble(), history);
    }

    private static Instant time(JsonNode value) throws IOException {
        if (!value.canConvertToLong() || value.asLong() <= 0) {
            throw new IOException("行情时间戳无效");
        }
        return Instant.ofEpochSecond(value.asLong());
    }

    private static boolean positive(JsonNode value) {
        return value.asDouble() > 0 && Double.isFinite(value.asDouble());
    }

    private GlobalMarketSeries series(String symbol, String source, String status, Instant at, double price, double reference,
                                      TreeMap<String, Double> history) throws IOException {
        if (!Double.isFinite(price) || !Double.isFinite(reference) || price <= 0 || reference <= 0 || history.size() < 2
                || at.isAfter(Instant.now().plusSeconds(300))) {
            throw new IOException("缺少可验证的报价或历史日线");
        }
        return new GlobalMarketSeries(symbol, source, status, at.toString(), price, reference, history);
    }
}
