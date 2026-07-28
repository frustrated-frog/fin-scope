package com.finscope.service.marketintel;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapitalSnapshotFreshnessPolicyTest {
    private final CapitalSnapshotFreshnessPolicy policy = new CapitalSnapshotFreshnessPolicy(15);

    @Test
    void requires_recent_intraday_fact_during_trading() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 28, 10, 30);

        assertTrue(policy.isFresh(LocalDateTime.of(2026, 7, 28, 10, 15), now));
        assertFalse(policy.isFresh(LocalDateTime.of(2026, 7, 28, 10, 14, 59), now));
        assertFalse(policy.isFresh(LocalDateTime.of(2026, 7, 27, 15, 0), now));
    }

    @Test
    void aligns_lunch_close_and_weekend_to_latest_expected_market_fact() {
        assertTrue(policy.isFresh(
                LocalDateTime.of(2026, 7, 28, 11, 20),
                LocalDateTime.of(2026, 7, 28, 12, 30)));
        assertFalse(policy.isFresh(
                LocalDateTime.of(2026, 7, 28, 11, 14),
                LocalDateTime.of(2026, 7, 28, 12, 30)));
        assertTrue(policy.isFresh(
                LocalDateTime.of(2026, 7, 24, 14, 50),
                LocalDateTime.of(2026, 7, 26, 10, 0)));
        assertFalse(policy.isFresh(
                LocalDateTime.of(2026, 7, 23, 15, 0),
                LocalDateTime.of(2026, 7, 26, 10, 0)));
    }

    @Test
    void rejects_future_timestamps_beyond_clock_skew() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 28, 10, 30);

        assertFalse(policy.isFresh(LocalDateTime.of(2026, 7, 28, 10, 36), now));
    }
}
