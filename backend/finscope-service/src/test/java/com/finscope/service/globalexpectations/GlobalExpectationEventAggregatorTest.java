package com.finscope.service.globalexpectations;

import com.finscope.domain.globalexpectations.GlobalExpectationEventGroup;
import com.finscope.domain.globalexpectations.GlobalExpectationItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExpectationEventAggregatorTest {
    @Test
    void groupsSiblingOutcomesByOfficialEventAndDeduplicatesCrossCategoryMarkets() {
        GlobalExpectationItem unchanged = item("fed-event", "m1", "利率保持不变？", "财务", 75, 80);
        GlobalExpectationItem hike = item("fed-event", "m2", "加息25个基点？", "经济", 25, 60);
        GlobalExpectationItem duplicate = item("fed-event", "m1", "利率保持不变？", "经济", 75, 80);

        List<GlobalExpectationEventGroup> groups = new GlobalExpectationEventAggregator()
                .aggregate(List.of(unchanged, hike, duplicate));

        assertEquals(1, groups.size());
        assertEquals("2026年9月美联储利率决议", groups.get(0).getTitle());
        assertEquals(List.of("财务", "经济"), groups.get(0).getThemes());
        assertEquals(2, groups.get(0).getMarkets().size());
        assertEquals(80, groups.get(0).getSignalScore());
        assertEquals("SIGNAL", groups.get(0).getStatus());
    }

    private GlobalExpectationItem item(String eventId, String marketId, String question, String theme,
                                       int probability, int signalScore) {
        GlobalExpectationItem item = new GlobalExpectationItem();
        item.setEventId(eventId);
        item.setEventTitle("2026年9月美联储利率决议");
        item.setMarketId(marketId);
        item.setMarketUrl("https://polymarket.com/event/" + marketId);
        item.setQuestion(question);
        item.setTheme(theme);
        item.setProbability(probability);
        item.setVolume24h(100000D);
        item.setSignalScore(signalScore);
        item.setSignalReasons(List.of("1小时概率显著变化"));
        item.setStatus(signalScore >= 40 ? "SIGNAL" : "WATCHING");
        return item;
    }
}
