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
    void fetchesTwoVolumeOrderedPagesInsteadOfAssumingGammaAcceptsFiveHundredItems() throws Exception {
        List<URI> requested = new ArrayList<URI>();
        FinanceHttpClient httpClient = new FinanceHttpClient() {
            @Override
            public FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) {
                requested.add(uri);
                String id = uri.getQuery().contains("offset=0") ? "market-1" : "market-2";
                String body = "[{\"id\":\"" + id + "\",\"question\":\"Will oil rise?\","
                        + "\"slug\":\"" + id + "\",\"outcomePrices\":\"[\\\"0.31\\\",\\\"0.69\\\"]\"}]";
                return new FinanceHttpResponse(200, body, Instant.now(), "hash");
            }
        };
        PolymarketPublicClient client = new PolymarketPublicClient();
        ReflectionTestUtils.setField(client, "financeHttpClient", httpClient);

        List<PolymarketPublicMarket> markets = client.fetchActiveMarkets();

        assertEquals(2, markets.size());
        assertEquals(2, requested.size());
        assertEquals("closed=false&limit=100&offset=0&order=volumeNum&ascending=false&locale=zh",
                requested.get(0).getQuery());
        assertEquals("closed=false&limit=100&offset=100&order=volumeNum&ascending=false&locale=zh",
                requested.get(1).getQuery());
    }

    @Test
    void parsesYesPriceAndMarketMetadataFromGammaResponse() throws Exception {
        String response = "[{\"id\":\"market-1\",\"question\":\"Will oil exceed $100?\","
                + "\"slug\":\"oil-100\",\"outcomePrices\":\"[\\\"0.31\\\",\\\"0.69\\\"]\","
                + "\"clobTokenIds\":\"[\\\"yes-token\\\",\\\"no-token\\\"]\","
                + "\"oneHourPriceChange\":0.024,\"oneDayPriceChange\":-0.071,"
                + "\"volumeNum\":856000,\"liquidityNum\":291000,\"endDate\":\"2026-12-31T12:00:00Z\"}]";

        List<PolymarketPublicMarket> markets = PolymarketPublicClient.parseActiveMarkets(response);

        assertEquals(1, markets.size());
        assertEquals("Will oil exceed $100?", markets.get(0).getQuestion());
        assertEquals(31, markets.get(0).getYesProbability());
        assertEquals("yes-token", markets.get(0).getYesTokenId());
        assertEquals(0.024D, markets.get(0).getOneHourPriceChange());
        assertEquals(-0.071D, markets.get(0).getOneDayPriceChange());
        assertEquals("https://polymarket.com/event/oil-100", markets.get(0).getMarketUrl());
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
