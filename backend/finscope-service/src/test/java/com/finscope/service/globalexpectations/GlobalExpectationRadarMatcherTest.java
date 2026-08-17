package com.finscope.service.globalexpectations;

import com.finscope.domain.globalexpectations.GlobalExpectationEventGroup;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarSignal;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finscope.dao.radar.RadarRepository;

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

    @Test
    void summarizesRecentWindowsAndIndependentSourcesForMatchedEvents() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 14, 0);
        GlobalExpectationEventGroup group = new GlobalExpectationEventGroup();
        group.setTitle("2026年9月美联储利率决议");
        RadarEvent related = event(7L, "美联储九月会议利率决定受到通胀数据影响");
        RadarRepository repository = mock(RadarRepository.class);
        when(repository.findEventsSince(any(LocalDateTime.class), eq(300))).thenReturn(List.of(related));
        when(repository.findSignalsByEventId(7L)).thenReturn(List.of(
                signal("Reuters", now.minusMinutes(12)),
                signal("Bloomberg", now.minusMinutes(35)),
                signal("Reuters", now.minusMinutes(82)),
                signal("WSJ", now.minusHours(6))));
        GlobalExpectationRadarMatcher matcher = new GlobalExpectationRadarMatcher();
        ReflectionTestUtils.setField(matcher, "radarRepository", repository);

        matcher.attachRecent(List.of(group), now);

        assertEquals("READY", group.getRealityDataStatus());
        assertEquals(2, group.getRadarMatches().get(0).getNewsCount1h());
        assertEquals(1, group.getRadarMatches().get(0).getNewsCountPrevious1h());
        assertEquals(4, group.getRadarMatches().get(0).getNewsCount24h());
        assertEquals(3, group.getRadarMatches().get(0).getIndependentSourceCount());
        assertNotNull(group.getRadarMatches().get(0).getLastSeenAt());
    }

    @Test
    void marksRealityDataAsFailedWhenLocalRadarCannotBeRead() {
        GlobalExpectationEventGroup group = new GlobalExpectationEventGroup();
        RadarRepository repository = mock(RadarRepository.class);
        when(repository.findEventsSince(any(LocalDateTime.class), eq(300)))
                .thenThrow(new IllegalStateException("database unavailable"));
        GlobalExpectationRadarMatcher matcher = new GlobalExpectationRadarMatcher();
        ReflectionTestUtils.setField(matcher, "radarRepository", repository);

        matcher.attachRecent(List.of(group));

        assertEquals("FAILED", group.getRealityDataStatus());
        assertEquals(List.of(), group.getRadarMatches());
    }

    private RadarEvent event(Long id, String title) {
        RadarEvent event = new RadarEvent();
        event.setId(id);
        event.setCanonicalTitle(title);
        event.setSummary(title);
        return event;
    }

    private RadarSignal signal(String sourceName, LocalDateTime publishedAt) {
        RadarSignal signal = new RadarSignal();
        signal.setSourceName(sourceName);
        signal.setPublishedAt(publishedAt);
        signal.setLastSeenAt(publishedAt);
        return signal;
    }
}
