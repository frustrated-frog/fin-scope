package com.finscope.service.globalexpectations;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExpectationSnapshotCacheTest {
    @Test
    void calculatesChangeAgainstTheLatestSnapshotBeforeWindowStart() {
        GlobalExpectationSnapshotCache cache = new GlobalExpectationSnapshotCache();
        Instant start = Instant.parse("2026-08-15T00:00:00Z");
        cache.record("oil", start, 27);
        cache.record("oil", start.plus(Duration.ofMinutes(5)), 31);

        assertEquals(4.0D, cache.changeSince("oil", start.plus(Duration.ofMinutes(5)), 31,
                Duration.ofMinutes(5)));
    }

    @Test
    void keepsOnePointWhenPriceDidNotChangeAndPrunesExpiredSnapshots() {
        GlobalExpectationSnapshotCache cache = new GlobalExpectationSnapshotCache();
        Instant start = Instant.parse("2026-08-14T00:00:00Z");
        cache.record("oil", start, 27);
        cache.record("oil", start.plus(Duration.ofMinutes(1)), 27);
        cache.record("oil", start.plus(Duration.ofHours(25)), 31);

        assertEquals(1, cache.history("oil").size());
        assertNull(cache.changeSince("oil", start.plus(Duration.ofHours(25)), 31, Duration.ofHours(24)));
    }
}
