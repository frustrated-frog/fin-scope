package com.finscope.service.globalexpectations;

import com.finscope.domain.globalexpectations.GlobalExpectationEventGroup;
import com.finscope.domain.radar.RadarEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExpectationRadarMatcherTest {
    @Test
    void attachesOnlyLocallyStoredRadarEventsWithMeaningfulTextOverlap() {
        GlobalExpectationEventGroup group = new GlobalExpectationEventGroup();
        group.setTitle("2026年9月美联储利率决议");
        RadarEvent related = event(7L, "美联储九月会议利率决定受到通胀数据影响");
        RadarEvent unrelated = event(8L, "某新能源公司发布新车型");

        new GlobalExpectationRadarMatcher().attach(List.of(group), List.of(related, unrelated));

        assertEquals(1, group.getRadarMatches().size());
        assertEquals(7L, group.getRadarMatches().get(0).getEventId());
    }

    private RadarEvent event(Long id, String title) {
        RadarEvent event = new RadarEvent();
        event.setId(id);
        event.setCanonicalTitle(title);
        event.setSummary(title);
        return event;
    }
}
