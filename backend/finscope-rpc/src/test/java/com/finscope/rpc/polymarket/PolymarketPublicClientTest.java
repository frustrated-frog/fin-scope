package com.finscope.rpc.polymarket;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolymarketPublicClientTest {
    @Test
    void parsesYesPriceAndMarketMetadataFromGammaResponse() throws Exception {
        String response = "[{\"id\":\"market-1\",\"question\":\"Will oil exceed $100?\","
                + "\"slug\":\"oil-100\",\"outcomePrices\":\"[\\\"0.31\\\",\\\"0.69\\\"]\","
                + "\"volumeNum\":856000,\"liquidityNum\":291000,\"endDate\":\"2026-12-31T12:00:00Z\"}]";

        List<PolymarketPublicMarket> markets = PolymarketPublicClient.parseActiveMarkets(response);

        assertEquals(1, markets.size());
        assertEquals("Will oil exceed $100?", markets.get(0).getQuestion());
        assertEquals(31, markets.get(0).getYesProbability());
        assertEquals("https://polymarket.com/event/oil-100", markets.get(0).getMarketUrl());
    }
}
