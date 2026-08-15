package com.finscope.service.globalexpectations;

import com.finscope.domain.globalexpectations.GlobalExpectationItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExpectationSignalDetectorTest {
    @Test
    void detectsRankRiseProbabilityCrossingAndVolumeAccelerationFromSnapshots() {
        GlobalExpectationItem previous = item("market-a", 47, 90000D);
        previous.setRank(7);
        GlobalExpectationItem current = item("market-a", 54, 150000D);
        current.setChange1h(7D);

        new GlobalExpectationSignalDetector().enrich(List.of(current), List.of(previous));

        assertEquals(1, current.getRank());
        assertEquals(6, current.getRankChange());
        assertTrue(current.getSignalReasons().contains("突破50%分歧线"));
        assertTrue(current.getSignalReasons().contains("24h成交量明显加速"));
        assertEquals("SIGNAL", current.getStatus());
        assertTrue(current.getSignalScore() >= 70);
    }

    @Test
    void marksARealTopTenNewEntryButDoesNotFlagTheInitialSnapshot() {
        GlobalExpectationItem retained = item("market-old", 40, 200000D);
        GlobalExpectationItem newcomer = item("market-new", 42, 180000D);
        GlobalExpectationItem previous = item("market-old", 39, 190000D);

        GlobalExpectationSignalDetector detector = new GlobalExpectationSignalDetector();
        detector.enrich(List.of(retained, newcomer), List.of(previous));

        assertTrue(newcomer.getSignalReasons().contains("新进入分类成交榜"));
        GlobalExpectationItem initial = item("market-first", 42, 180000D);
        detector.enrich(List.of(initial), List.of());
        assertEquals("WATCHING", initial.getStatus());
    }

    private GlobalExpectationItem item(String marketId, int probability, double volume24h) {
        GlobalExpectationItem item = new GlobalExpectationItem();
        item.setMarketId(marketId);
        item.setTheme("政治");
        item.setMarketUrl("https://polymarket.com/event/" + marketId);
        item.setProbability(probability);
        item.setVolume24h(volume24h);
        return item;
    }
}
