package com.finscope.service.radar;

import com.finscope.domain.radar.NewsworthinessAssessment;
import com.finscope.domain.radar.NewswireRankingPolicy;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.service.dashboard.DashboardHotspotRankingService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class NewswireSelectionTest {
    @Test
    void ignoredEventsDoNotConsumeDiversitySlotsAndFilteringHappensBeforeLimit() {
        var ignored = event(1L, "A", 100);
        var a = event(2L, "A", 90);
        var b = event(3L, "B", 85);
        RadarEventWorkspace.Summary summary = new RadarEventWorkspace.Summary(); summary.setDisposition("IGNORED");
        var cards = List.of(new ResearchRadarView.EventCard(ignored, null, summary), new ResearchRadarView.EventCard(a), new ResearchRadarView.EventCard(b));
        var result = RadarEventCardQuery.ranked(cards, List.of(ignored, b, a), "ALL", 1);
        assertEquals(2L, result.get(0).getId());
        assertEquals(1, RadarEventCardQuery.counts(cards).get("IGNORED"));
        assertEquals(2, RadarEventCardQuery.counts(cards).get("ALL"));
    }

    @Test
    void publicBoardUsesPublicValueWhilePersonalOrderCanUseItsSmallBonus() {
        var a = event(1L, "A", 80); a.setPriorityScore(92); a.setHotspotScore(20);
        var b = event(2L, "B", 85); b.setPriorityScore(85); b.setHotspotScore(99);
        assertEquals(List.of(b, a), NewswireRankingPolicy.rank(List.of(a, b), false));
        assertEquals(List.of(a, b), NewswireRankingPolicy.rank(List.of(a, b), true));
        var ranking = new DashboardHotspotRankingService().rankings(List.of(a, b)).get(0);
        assertEquals(List.of(2L, 1L), ranking.getItems().stream().map(DashboardHotspotRankingService.Item::getId).toList());
    }

    @Test
    void unupgradedEventsRetainTheirPreviousHeatAndStableTieOrder() {
        var a = new RadarEvent(); a.setId(1L); a.setHotspotScore(70);
        var b = new RadarEvent(); b.setId(2L); b.setHotspotScore(70);
        assertEquals(List.of(b, a), NewswireRankingPolicy.rank(List.of(a, b), false));
        assertEquals(70, NewswireRankingPolicy.publicScore(a));
    }

    private RadarEvent event(Long id, String subject, int score) {
        var e = new RadarEvent(); e.setId(id); e.setEventKey("event:" + id); e.setCategoryCode("COMPANY");
        e.setDashboardCategory("FINANCE"); e.setStatus("ACTIVE"); e.setCanonicalTitle("测试事件" + id);
        var n = new NewsworthinessAssessment(); n.setPublicScore(score); n.setDiversityKey(subject);
        e.setNewsworthiness(n); e.setPriorityScore(score); return e;
    }
}
