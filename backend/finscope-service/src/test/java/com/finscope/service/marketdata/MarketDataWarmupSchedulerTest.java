package com.finscope.service.marketdata;

import com.finscope.domain.instrument.SectorCategory;
import com.finscope.service.instrument.MarketIndexService;
import com.finscope.service.instrument.SectorMarketService;
import com.finscope.service.instrument.WatchlistService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MarketDataWarmupSchedulerTest {
    private final WatchlistService watchlist = mock(WatchlistService.class);
    private final MarketIndexService indices = mock(MarketIndexService.class);
    private final SectorMarketService sectors = mock(SectorMarketService.class);

    @Test
    void warmsIndependentMarketCapabilitiesDuringTrading() {
        scheduler(true, LocalDateTime.of(2026, 7, 24, 10, 0)).refreshHotMarketData();

        verify(watchlist).listInvestmentItemsWithQuotes(true);
        verify(indices).list(true);
        verify(sectors).overview(SectorCategory.INDUSTRY, 5, true);
        verify(sectors).overview(SectorCategory.CONCEPT, 5, true);
    }

    @Test
    void skipsWarmupOutsideTradingOrWhenDisabled() {
        scheduler(true, LocalDateTime.of(2026, 7, 24, 12, 0)).refreshHotMarketData();
        scheduler(false, LocalDateTime.of(2026, 7, 24, 10, 0)).refreshHotMarketData();

        verify(watchlist, never()).listInvestmentItemsWithQuotes(true);
        verify(indices, never()).list(true);
    }

    @Test
    void oneCapabilityFailureDoesNotSuppressOthers() {
        doThrow(new IllegalStateException("watchlist failed"))
                .when(watchlist).listInvestmentItemsWithQuotes(true);

        scheduler(true, LocalDateTime.of(2026, 7, 24, 10, 0)).refreshHotMarketData();

        verify(indices).list(true);
        verify(sectors).overview(SectorCategory.INDUSTRY, 5, true);
        verify(sectors).overview(SectorCategory.CONCEPT, 5, true);
    }

    private MarketDataWarmupScheduler scheduler(boolean enabled, LocalDateTime now) {
        Clock clock = Clock.fixed(now.toInstant(ZoneOffset.ofHours(8)), ZoneOffset.ofHours(8));
        return new MarketDataWarmupScheduler(watchlist, indices, sectors,
                new MarketTradingSession(clock, 120L), Runnable::run, enabled);
    }
}
