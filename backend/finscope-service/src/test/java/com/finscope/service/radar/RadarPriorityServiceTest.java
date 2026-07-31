package com.finscope.service.radar;

import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarPriorityServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 31, 16, 0);
    private RadarPriorityService service;

    @BeforeEach
    void setUp() { service = new RadarPriorityService(); }

    @Test
    void scoresDirectWatchlistMatchAndExplainsEveryPoint() {
        RadarPriorityService.PriorityResult result = service.score(
                event("宁德时代发布新一代电池", NOW.minusMinutes(30)),
                Arrays.asList(signal("CLS", "TIER_1", "宁德时代发布新一代电池", NOW.minusMinutes(30)),
                        signal("THS", "TIER_1", "宁德时代新电池正式发布", NOW.minusMinutes(20))),
                Collections.singletonList(watchlist("300750", "宁德时代")), NOW);

        assertEquals(25, result.getWatchlistScore());
        assertTrue(result.getReasons().contains("与自选「宁德时代」直接相关"));
        assertEquals(result.componentTotal(), result.getTotalScore());
        assertTrue(result.getNoveltyScore() <= 25);
        assertTrue(result.getSourceDiversityScore() <= 20);
        assertTrue(result.getSourceQualityScore() <= 15);
        assertTrue(result.getRecencyScore() <= 15);
    }

    @Test
    void matchesExplicitSecurityCode() {
        RadarPriorityService.PriorityResult result = service.score(
                event("300750发布业绩预告", NOW.minusHours(2)),
                Collections.singletonList(signal("CLS", "TIER_1", "300750发布业绩预告", NOW.minusHours(2))),
                Collections.singletonList(watchlist("300750", "宁德时代")), NOW);

        assertEquals(25, result.getWatchlistScore());
        assertEquals("与自选「宁德时代」直接相关", result.getWatchlistExplanation());
    }

    @Test
    void doesNotInventWatchlistRelationship() {
        RadarPriorityService.PriorityResult result = service.score(
                event("美联储维持利率不变", NOW.minusHours(3)),
                Collections.singletonList(signal("CLS", "TIER_2", "美联储维持利率不变", NOW.minusHours(3))),
                Collections.singletonList(watchlist("300750", "宁德时代")), NOW);

        assertEquals(0, result.getWatchlistScore());
        assertEquals("未发现与当前自选标的的直接关系", result.getWatchlistExplanation());
    }

    @Test
    void sameProviderRepostsCountAsOneIndependentSource() {
        RadarPriorityService.PriorityResult result = service.score(
                event("黄金价格刷新高点", NOW.minusHours(1)),
                Arrays.asList(signal("CLS", "TIER_1", "黄金价格刷新高点", NOW.minusHours(1)),
                        signal("CLS", "TIER_1", "黄金价格再创新高", NOW.minusMinutes(50))),
                Collections.<WatchlistItem>emptyList(), NOW);

        assertEquals(8, result.getSourceDiversityScore());
    }

    @Test
    void missingTimeAndLowTierSourceReceiveNoHiddenBonus() {
        RadarPriorityService.PriorityResult result = service.score(
                event("市场传闻待确认", null),
                Collections.singletonList(signal("UNKNOWN", "TIER_3", "市场传闻待确认", null)),
                Collections.<WatchlistItem>emptyList(), NOW);

        assertEquals(0, result.getRecencyScore());
        assertEquals(5, result.getSourceQualityScore());
        assertTrue(result.getUncertainty().contains("发布时间"));
    }

    private RadarEvent event(String title, LocalDateTime time) {
        RadarEvent event = new RadarEvent();
        event.setCanonicalTitle(title); event.setSummary(title); event.setFirstSeenAt(time); event.setLastSeenAt(time);
        return event;
    }

    private RadarSignal signal(String provider, String tier, String title, LocalDateTime time) {
        RadarSignal signal = new RadarSignal();
        signal.setProviderCode(provider); signal.setSourceName(provider); signal.setSourceTier(tier);
        signal.setTitle(title); signal.setContent(title); signal.setPublishedAt(time); signal.setFirstSeenAt(time);
        return signal;
    }

    private WatchlistItem watchlist(String code, String name) {
        WatchlistItem item = new WatchlistItem(); item.setCode(code); item.setName(name); item.setType("STOCK"); return item;
    }
}
