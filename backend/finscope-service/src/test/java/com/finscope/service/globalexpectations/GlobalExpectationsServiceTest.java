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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    void usesRedisHistoryAsPartialWhenClobHistoryFails() {
        FakeCache cache = new FakeCache();
        cache.history = cachedHistory(Instant.now().minusSeconds(360).getEpochSecond(), 27.0D);
        PolymarketPublicClient client = new PolymarketPublicClient() {
            @Override
            public List<PolymarketPublicMarket> fetchActiveMarkets() {
                return List.of(market());
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
            public List<PolymarketPublicMarket> fetchActiveMarkets() {
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
            public List<PolymarketPublicMarket> fetchActiveMarkets() {
                return markets;
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
        PolymarketPublicMarket market = new PolymarketPublicMarket();
        market.setMarketId("oil-100");
        market.setQuestion("Will oil exceed $100 this year?");
        market.setMarketUrl("https://polymarket.com/event/oil-100");
        market.setYesTokenId("yes-token");
        market.setYesProbability(31);
        market.setOneHourPriceChange(0.024D);
        market.setOneDayPriceChange(-0.071D);
        market.setVolume(500000D);
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
