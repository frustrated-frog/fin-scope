package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEventSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RadarLifecycleServiceTest {
    private final RadarLifecycleService service = new RadarLifecycleService();
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 13, 10, 0);

    @Test
    void requiresMeaningfulIndependentGrowthBeforeRising() {
        RadarEventSnapshot previous = snapshot("STABLE", 60, 2, now.minusMinutes(10));

        assertEquals("STABLE", service.next(previous, 72, 2, now.minusMinutes(5), now));
        assertEquals("RISING", service.next(previous, 72, 4, now.minusMinutes(5), now));
    }

    @Test
    void usesHysteresisBeforeCoolingAndQuiet() {
        RadarEventSnapshot previous = snapshot("PEAK", 86, 4, now.minusMinutes(10));

        assertEquals("STABLE", service.next(previous, 80, 4, now.minusMinutes(20), now));
        assertEquals("COOLING", service.next(previous, 70, 4, now.minusMinutes(70), now));
        assertEquals("QUIET", service.next(previous, 30, 4, now.minusHours(3), now));
    }

    private RadarEventSnapshot snapshot(String state, int score, int sources, LocalDateTime at) {
        RadarEventSnapshot value = new RadarEventSnapshot();
        value.setLifecycleState(state);
        value.setHotnessScore(score);
        value.setIndependentSourceCount(sources);
        value.setSnapshotAt(at);
        return value;
    }
}
