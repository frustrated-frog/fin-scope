package com.finscope.rpc.polymarket;

import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolymarketPublicClientTest {
    @Test
    void resolvesOfficialTagAndFetchesCategoryTopTenByDailyVolume() throws Exception {
        List<URI> requested = new ArrayList<URI>();
        FinanceHttpClient httpClient = new FinanceHttpClient() {
            @Override
            public FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) {
                requested.add(uri);
                String body = uri.getPath().startsWith("/tags/slug/")
                        ? "{\"id\":\"2\",\"slug\":\"politics\"}"
                        : "[{\"id\":\"market-1\",\"question\":\"Will the bill pass?\","
                        + "\"slug\":\"market-1\",\"outcomePrices\":\"[\\\"0.31\\\",\\\"0.69\\\"]\"}]";
                return new FinanceHttpResponse(200, body, Instant.now(), "hash");
            }
        };
        PolymarketPublicClient client = new PolymarketPublicClient();
        ReflectionTestUtils.setField(client, "financeHttpClient", httpClient);

        List<PolymarketPublicMarket> markets = client.fetchTopMarketsByCategory("politics", 10);

        assertEquals(1, markets.size());
        assertEquals(2, requested.size());
        assertEquals("/tags/slug/politics", requested.get(0).getPath());
        assertEquals("active=true&closed=false&tag_id=2&related_tags=true&limit=10"
                        + "&order=volume24hr&ascending=false&locale=zh",
                requested.get(1).getQuery());
    }

    @Test
    void parsesYesPriceAndMarketMetadataFromGammaResponse() throws Exception {
        String response = "[{\"id\":\"market-1\",\"question\":\"Will oil exceed $100?\","
                + "\"slug\":\"oil-100\",\"outcomePrices\":\"[\\\"0.31\\\",\\\"0.69\\\"]\","
                + "\"clobTokenIds\":\"[\\\"yes-token\\\",\\\"no-token\\\"]\","
                + "\"oneHourPriceChange\":0.024,\"oneDayPriceChange\":-0.071,"
                + "\"volumeNum\":856000,\"volume24hr\":128000,\"liquidityNum\":291000,"
                + "\"endDate\":\"2026-12-31T12:00:00Z\","
                + "\"events\":[{\"id\":\"fed-september\",\"title\":\"September Fed decision\","
                + "\"slug\":\"september-fed-decision\"}]}]";

        List<PolymarketPublicMarket> markets = PolymarketPublicClient.parseActiveMarkets(response);

        assertEquals(1, markets.size());
        assertEquals("Will oil exceed $100?", markets.get(0).getQuestion());
        assertEquals(31, markets.get(0).getYesProbability());
        assertEquals("yes-token", markets.get(0).getYesTokenId());
        assertEquals(0.024D, markets.get(0).getOneHourPriceChange());
        assertEquals(-0.071D, markets.get(0).getOneDayPriceChange());
        assertEquals(128000D, markets.get(0).getVolume24h());
        assertEquals("fed-september", markets.get(0).getEventId());
        assertEquals("September Fed decision", markets.get(0).getEventTitle());
        assertEquals("september-fed-decision", markets.get(0).getEventSlug());
        assertEquals("https://polymarket.com/event/oil-100", markets.get(0).getMarketUrl());
    }

    @Test
    void splitsLargeHistoryRequestsIntoBatchesOfTwenty() throws Exception {
        List<String> requestBodies = new ArrayList<String>();
        FinanceHttpClient httpClient = new FinanceHttpClient() {
            @Override
            public FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) {
                throw new UnsupportedOperationException();
            }

            @Override
            public FinanceHttpResponse postJson(String providerCode, URI uri, String body,
                                                Map<String, String> headers) {
                requestBodies.add(body);
                return new FinanceHttpResponse(200, "{\"history\":{}}", Instant.now(), "hash");
            }
        };
        PolymarketPublicClient client = new PolymarketPublicClient();
        ReflectionTestUtils.setField(client, "financeHttpClient", httpClient);
        List<String> tokenIds = new ArrayList<String>();
        for (int index = 0; index < 50; index++) {
            tokenIds.add("token-" + index);
        }

        client.fetchPriceHistory(tokenIds);

        assertEquals(3, requestBodies.size());
    }

    @Test
    void parsesBatchHistoryByTokenId() throws Exception {
        String response = "{\"history\":{\"yes-token\":[{\"t\":1786748100,\"p\":0.27},"
                + "{\"t\":1786748400,\"p\":0.31}]}}";

        Map<String, List<PolymarketPricePoint>> history = PolymarketPublicClient.parseBatchHistory(response);

        assertEquals(2, history.get("yes-token").size());
        assertEquals(1786748100L, history.get("yes-token").get(0).getTimestamp());
        assertEquals(0.31D, history.get("yes-token").get(1).getPrice());
    }
}
