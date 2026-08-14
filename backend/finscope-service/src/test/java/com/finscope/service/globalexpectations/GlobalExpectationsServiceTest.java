package com.finscope.service.globalexpectations;

import com.finscope.domain.globalexpectations.GlobalExpectationItem;
import com.finscope.rpc.polymarket.PolymarketPublicClient;
import com.finscope.rpc.polymarket.PolymarketPublicMarket;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExpectationsServiceTest {
    @Test
    void retainsTheLastSuccessfulViewAndCalculatesFiveMinuteChange() {
        PolymarketPublicMarket market = market("oil-100", 31);
        PolymarketPublicClient client = new PolymarketPublicClient() {
            @Override
            public List<PolymarketPublicMarket> fetchActiveMarkets() {
                return List.of(market);
            }
        };
        GlobalExpectationSnapshotCache cache = new GlobalExpectationSnapshotCache();
        cache.record("oil-100", Instant.now().minus(Duration.ofMinutes(6)), 27);
        GlobalExpectationsService service = new GlobalExpectationsService();
        ReflectionTestUtils.setField(service, "polymarketPublicClient", client);
        ReflectionTestUtils.setField(service, "catalog", new GlobalExpectationsCatalog());
        ReflectionTestUtils.setField(service, "snapshotCache", cache);

        List<GlobalExpectationItem> refreshed = service.refresh();

        assertEquals(1, refreshed.size());
        assertEquals(4.0D, refreshed.get(0).getChange5m());
        assertEquals("LIVE", refreshed.get(0).getDataStatus());
    }

    @Test
    void retainsLastSuccessfulItemsWhenPublicSourceFails() {
        AtomicBoolean fail = new AtomicBoolean(false);
        PolymarketPublicClient client = new PolymarketPublicClient() {
            @Override
            public List<PolymarketPublicMarket> fetchActiveMarkets() throws Exception {
                if (fail.get()) {
                    throw new IllegalStateException("temporary provider failure");
                }
                return List.of(market("oil-100", 31));
            }
        };
        GlobalExpectationsService service = new GlobalExpectationsService();
        ReflectionTestUtils.setField(service, "polymarketPublicClient", client);
        ReflectionTestUtils.setField(service, "catalog", new GlobalExpectationsCatalog());
        ReflectionTestUtils.setField(service, "snapshotCache", new GlobalExpectationSnapshotCache());
        service.refresh();
        fail.set(true);

        List<GlobalExpectationItem> retained = service.refresh();

        assertEquals("STALE", retained.get(0).getDataStatus());
        assertEquals(31, retained.get(0).getProbability());
    }

    private PolymarketPublicMarket market(String marketId, int probability) {
        PolymarketPublicMarket market = new PolymarketPublicMarket();
        market.setMarketId(marketId);
        market.setQuestion("Will oil exceed $100 this year?");
        market.setMarketUrl("https://polymarket.com/event/oil-100");
        market.setYesProbability(probability);
        market.setVolume(500000D);
        return market;
    }
}
