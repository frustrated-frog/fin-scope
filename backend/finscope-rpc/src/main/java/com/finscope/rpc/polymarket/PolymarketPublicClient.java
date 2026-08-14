package com.finscope.rpc.polymarket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 仅访问 Polymarket Gamma 公共市场接口；不携带钱包、签名或交易凭证。 */
@Component
public class PolymarketPublicClient {
    private static final URI ACTIVE_MARKETS_URI = URI.create(
            "https://gamma-api.polymarket.com/markets?closed=false&limit=500&offset=0");
    private static final URI BATCH_HISTORY_URI = URI.create("https://clob.polymarket.com/batch-prices-history");
    private static final int MAX_HISTORY_MARKETS = 20;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private FinanceHttpClient financeHttpClient;

    public List<PolymarketPublicMarket> fetchActiveMarkets() throws Exception {
        FinanceHttpResponse response = financeHttpClient.get("polymarket-gamma", ACTIVE_MARKETS_URI,
                Map.of("Accept", "application/json"));
        return parseActiveMarkets(response.getBody());
    }

    public Map<String, List<PolymarketPricePoint>> fetchPriceHistory(List<String> tokenIds) throws Exception {
        if (tokenIds == null || tokenIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> markets = tokenIds.size() > MAX_HISTORY_MARKETS
                ? tokenIds.subList(0, MAX_HISTORY_MARKETS) : tokenIds;
        String body = OBJECT_MAPPER.writeValueAsString(Map.of(
                "markets", markets,
                "interval", "1d",
                "fidelity", 1));
        FinanceHttpResponse response = financeHttpClient.postJson("polymarket-clob", BATCH_HISTORY_URI, body,
                Map.of("Accept", "application/json", "Content-Type", "application/json"));
        return parseBatchHistory(response.getBody());
    }

    static List<PolymarketPublicMarket> parseActiveMarkets(String body) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(body);
        JsonNode items = root;
        if (root.isObject() && root.has("data")) {
            items = root.get("data");
        }
        if (root.isObject() && root.has("markets")) {
            items = root.get("markets");
        }
        if (!items.isArray()) {
            return Collections.emptyList();
        }
        List<PolymarketPublicMarket> markets = new ArrayList<PolymarketPublicMarket>();
        for (JsonNode item : items) {
            String question = text(item, "question");
            String slug = text(item, "slug");
            if (question.isBlank() || slug.isBlank()) {
                continue;
            }
            PolymarketPublicMarket market = new PolymarketPublicMarket();
            market.setMarketId(text(item, "id"));
            market.setQuestion(question);
            market.setMarketUrl("https://polymarket.com/event/" + slug);
            market.setYesTokenId(readFirstArrayValue(item, "clobTokenIds"));
            market.setYesProbability(readYesProbability(item));
            market.setOneHourPriceChange(number(item, "oneHourPriceChange", "priceChange1h"));
            market.setOneDayPriceChange(number(item, "oneDayPriceChange", "priceChange24h"));
            market.setVolume(number(item, "volumeNum", "volume"));
            market.setOpenInterest(number(item, "liquidityNum", "liquidity"));
            market.setEndDate(text(item, "endDate"));
            markets.add(market);
        }
        return markets;
    }

    static Map<String, List<PolymarketPricePoint>> parseBatchHistory(String body) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(body);
        JsonNode histories = root.has("history") ? root.get("history") : root;
        if (histories == null || !histories.isObject()) {
            return Collections.emptyMap();
        }
        Map<String, List<PolymarketPricePoint>> result = new LinkedHashMap<String, List<PolymarketPricePoint>>();
        histories.fields().forEachRemaining(entry -> {
            List<PolymarketPricePoint> points = new ArrayList<PolymarketPricePoint>();
            if (entry.getValue().isArray()) {
                for (JsonNode value : entry.getValue()) {
                    JsonNode timestamp = value.get("t");
                    JsonNode price = value.get("p");
                    if (timestamp == null || price == null || !timestamp.canConvertToLong()) {
                        continue;
                    }
                    PolymarketPricePoint point = new PolymarketPricePoint();
                    point.setTimestamp(timestamp.asLong());
                    point.setPrice(price.asDouble());
                    points.add(point);
                }
            }
            result.put(entry.getKey(), points);
        });
        return result;
    }

    private static Integer readYesProbability(JsonNode item) {
        String prices = text(item, "outcomePrices");
        if (!prices.isBlank()) {
            try {
                JsonNode values = OBJECT_MAPPER.readTree(prices);
                if (values.isArray() && values.size() > 0) {
                    return (int) Math.round(Double.parseDouble(values.get(0).asText()) * 100);
                }
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String readFirstArrayValue(JsonNode item, String field) {
        JsonNode source = item.get(field);
        if (source == null || source.isNull()) {
            return "";
        }
        try {
            JsonNode values = source.isTextual() ? OBJECT_MAPPER.readTree(source.asText()) : source;
            if (values.isArray() && !values.isEmpty()) {
                return values.get(0).asText("").trim();
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private static String text(JsonNode item, String field) {
        JsonNode value = item.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private static Double number(JsonNode item, String preferredField, String fallbackField) {
        JsonNode value = item.has(preferredField) ? item.get(preferredField) : item.get(fallbackField);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        try {
            return Double.valueOf(value.asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
