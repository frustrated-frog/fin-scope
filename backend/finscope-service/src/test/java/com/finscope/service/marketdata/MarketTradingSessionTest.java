package com.finscope.service.marketdata;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketTradingSessionTest {
    private final Clock clock = Clock.fixed(
            LocalDateTime.of(2026, 7, 24, 10, 0).toInstant(ZoneOffset.ofHours(8)),
            ZoneOffset.ofHours(8));
    private final MarketTradingSession session = new MarketTradingSession(clock, 120);

    @Test
    void recognizesMainlandTradingSessionsAndWeekends() {
        assertTrue(session.isOpen(LocalDateTime.of(2026, 7, 24, 9, 30)));
        assertTrue(session.isOpen(LocalDateTime.of(2026, 7, 24, 11, 30)));
        assertFalse(session.isOpen(LocalDateTime.of(2026, 7, 24, 12, 0)));
        assertTrue(session.isOpen(LocalDateTime.of(2026, 7, 24, 13, 0)));
        assertFalse(session.isOpen(LocalDateTime.of(2026, 7, 24, 15, 0, 1)));
        assertFalse(session.isOpen(LocalDateTime.of(2026, 7, 25, 10, 0)));
    }

    @Test
    void limitsIntradayFallbackToTwoMinutesButPreservesClosedMarketSnapshots() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 24, 10, 0);
        assertTrue(session.canServeFallback(now.minusSeconds(120), now));
        assertFalse(session.canServeFallback(now.minusSeconds(121), now));

        LocalDateTime noon = LocalDateTime.of(2026, 7, 24, 12, 0);
        assertTrue(session.canServeFallback(noon.minusDays(1), noon));
    }

    @Test
    void exposesCurrentSessionStateFromInjectedClock() {
        assertTrue(session.isOpenNow());
    }
}
