package com.finscope.service.globalexpectations;

import com.finscope.dao.cache.GlobalExpectationsCacheRepository;
import com.finscope.domain.globalexpectations.GlobalExpectationHistoryPoint;
import com.finscope.domain.globalexpectations.GlobalExpectationHistorySnapshot;
import com.finscope.domain.globalexpectations.GlobalExpectationItem;
import com.finscope.domain.globalexpectations.GlobalExpectationsViewSnapshot;
import com.finscope.rpc.polymarket.PolymarketPricePoint;
import com.finscope.rpc.polymarket.PolymarketPublicClient;
import com.finscope.rpc.polymarket.PolymarketPublicMarket;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExpectationsServiceTest {
    @Test
    void usesOfficialHourlyAndDailyChangesAndCalculatesFiveMinuteChangeFromHistory() {
        PolymarketPublicMarket market = market();
        FakeCache cache = new FakeCache();
        PolymarketPublicClient client = client(List.of(market), history(Instant.now().minusSeconds(360).getEpochSecond(), 0.27D));
        GlobalExpectationsService service = service(client, cache);

        List<GlobalExpectationItem> refreshed = service.refresh();

        assertEquals(4.0D, refreshed.get(0).getChange5m(), 0.001D);
        assertEquals(2.4D, refreshed.get(0).getChange1h(), 0.001D);
        assertEquals(-7.1D, refreshed.get(0).getChange24h(), 0.001D);
        assertEquals("LIVE", refreshed.get(0).getDataStatus());
        assertFalse(refreshed.get(0).getPriceHistory().isEmpty());
        assertEquals("yes-token", cache.history.getTokenId());
        assertEquals(1, cache.view.getItems().size());
    }

    @Test
    void keepsChineseQuestionWithoutDependingOnEnglishKeywordMatching() {
        PolymarketPublicMarket market = market();
        market.setQuestion("今年油价会超过100美元吗？");
        FakeCache cache = new FakeCache();
        GlobalExpectationsService service = service(client(List.of(market),
                history(Instant.now().minusSeconds(360).getEpochSecond(), 0.27D)), cache);

        List<GlobalExpectationItem> refreshed = service.refresh();

        assertEquals(1, refreshed.size());
        assertEquals("政治", refreshed.get(0).getTheme());
        assertEquals("今年油价会超过100美元吗？", refreshed.get(0).getQuestion());
    }

    @Test
    void keepsTenHighestDailyVolumeMarketsForEachOfficialCategory() {
        List<String> requestedCategories = new ArrayList<String>();
        PolymarketPublicClient client = new PolymarketPublicClient() {
            @Override
            public List<PolymarketPublicMarket> fetchTopMarketsByCategory(String categorySlug, int limit) {
                requestedCategories.add(categorySlug);
                List<PolymarketPublicMarket> markets = new ArrayList<PolymarketPublicMarket>();
                for (int index = 0; index < 12; index++) {
                    PolymarketPublicMarket market = market(categorySlug + "-" + index);
                    market.setVolume24h((double) index);
                    markets.add(market);
                }
                return markets;
            }

            @Override
            public Map<String, List<PolymarketPricePoint>> fetchPriceHistory(List<String> tokenIds) {
                return Map.of();
            }
        };

        List<GlobalExpectationItem> refreshed = service(client, new FakeCache()).refresh();

        assertEquals(List.of("politics", "finance", "geopolitics", "tech", "economy"), requestedCategories);
        assertEquals(50, refreshed.size());
        assertEquals(Map.of("政治", 10L, "财务", 10L, "地缘冲突", 10L, "科技", 10L, "经济", 10L),
                refreshed.stream().collect(Collectors.groupingBy(GlobalExpectationItem::getTheme,
                        Collectors.counting())));
        assertEquals(11D, refreshed.get(0).getVolume24h());
    }

    @Test
    void usesRedisHistoryAsPartialWhenClobHistoryFails() {
        FakeCache cache = new FakeCache();
        cache.history = cachedHistory(Instant.now().minusSeconds(360).getEpochSecond(), 27.0D);
        PolymarketPublicClient client = new PolymarketPublicClient() {
            @Override
            public List<PolymarketPublicMarket> fetchTopMarketsByCategory(String categorySlug, int limit) {
                return "politics".equals(categorySlug) ? List.of(market()) : List.of();
            }

            @Override
            public Map<String, List<PolymarketPricePoint>> fetchPriceHistory(List<String> tokenIds) {
                throw new IllegalStateException("clob unavailable");
            }
        };

        List<GlobalExpectationItem> refreshed = service(client, cache).refresh();

        assertEquals("PARTIAL", refreshed.get(0).getDataStatus());
        assertEquals(4.0D, refreshed.get(0).getChange5m(), 0.001D);
        assertEquals(2.4D, refreshed.get(0).getChange1h(), 0.001D);
    }

    @Test
    void usesRedisViewAsStaleWhenGammaFails() {
        FakeCache cache = new FakeCache();
        GlobalExpectationItem cachedItem = new GlobalExpectationItem();
        cachedItem.setProbability(31);
        cachedItem.setDataStatus("LIVE");
        cache.view = new GlobalExpectationsViewSnapshot();
        cache.view.setFetchedAt(Instant.now().minusSeconds(60).getEpochSecond());
        cache.view.setItems(List.of(cachedItem));
        PolymarketPublicClient client = new PolymarketPublicClient() {
            @Override
            public List<PolymarketPublicMarket> fetchTopMarketsByCategory(String categorySlug, int limit) {
                throw new IllegalStateException("gamma unavailable");
            }
        };

        List<GlobalExpectationItem> retained = service(client, cache).refresh();

        assertEquals(31, retained.get(0).getProbability());
        assertEquals("STALE", retained.get(0).getDataStatus());
    }

    private GlobalExpectationsService service(PolymarketPublicClient client, FakeCache cache) {
        GlobalExpectationsService service = new GlobalExpectationsService();
        ReflectionTestUtils.setField(service, "polymarketPublicClient", client);
        ReflectionTestUtils.setField(service, "catalog", new GlobalExpectationsCatalog());
        ReflectionTestUtils.setField(service, "cacheRepository", cache);
        return service;
    }

    private PolymarketPublicClient client(List<PolymarketPublicMarket> markets,
                                          List<PolymarketPricePoint> points) {
        return new PolymarketPublicClient() {
            @Override
            public List<PolymarketPublicMarket> fetchTopMarketsByCategory(String categorySlug, int limit) {
                return "politics".equals(categorySlug) ? markets : List.of();
            }

            @Override
            public Map<String, List<PolymarketPricePoint>> fetchPriceHistory(List<String> tokenIds) {
                return Map.of("yes-token", points);
            }
        };
    }

    private List<PolymarketPricePoint> history(long timestamp, double price) {
        PolymarketPricePoint point = new PolymarketPricePoint();
        point.setTimestamp(timestamp);
        point.setPrice(price);
        return List.of(point);
    }

    private GlobalExpectationHistorySnapshot cachedHistory(long timestamp, double probability) {
        GlobalExpectationHistoryPoint point = new GlobalExpectationHistoryPoint();
        point.setTimestamp(timestamp);
        point.setProbability(probability);
        GlobalExpectationHistorySnapshot snapshot = new GlobalExpectationHistorySnapshot();
        snapshot.setTokenId("yes-token");
        snapshot.setFetchedAt(Instant.now().getEpochSecond());
        snapshot.setPoints(List.of(point));
        return snapshot;
    }

    private PolymarketPublicMarket market() {
        return market("oil-100");
    }

    private PolymarketPublicMarket market(String marketId) {
        PolymarketPublicMarket market = new PolymarketPublicMarket();
        market.setMarketId(marketId);
        market.setQuestion("Will oil exceed $100 this year?");
        market.setMarketUrl("https://polymarket.com/event/" + marketId);
        market.setYesTokenId("oil-100".equals(marketId) ? "yes-token" : "yes-token-" + marketId);
        market.setYesProbability(31);
        market.setOneHourPriceChange(0.024D);
        market.setOneDayPriceChange(-0.071D);
        market.setVolume(500000D);
        market.setVolume24h(100000D);
        return market;
    }

    private static final class FakeCache implements GlobalExpectationsCacheRepository {
        private final Map<String, GlobalExpectationHistorySnapshot> histories = new HashMap<String, GlobalExpectationHistorySnapshot>();
        private GlobalExpectationHistorySnapshot history;
        private GlobalExpectationsViewSnapshot view;

        @Override
        public Optional<GlobalExpectationHistorySnapshot> getHistory(String tokenId) {
            return Optional.ofNullable(history == null ? histories.get(tokenId) : history);
        }

        @Override
        public void putHistory(GlobalExpectationHistorySnapshot snapshot) {
            history = snapshot;
            histories.put(snapshot.getTokenId(), snapshot);
        }

        @Override
        public Optional<GlobalExpectationsViewSnapshot> getView() {
            return Optional.ofNullable(view);
        }

        @Override
        public void putView(GlobalExpectationsViewSnapshot snapshot) {
            view = snapshot;
        }
    }
}
