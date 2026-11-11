package com.finscope.service.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.marketdata.MarketDataRefreshRunRepository;
import com.finscope.dao.marketdata.MarketDataSnapshotRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.CapitalFlowProvider;
import com.finscope.rpc.marketintel.CapitalReplayData;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MarketDataGatewayCapitalReplayTest {
    private final Clock clock = Clock.systemUTC();
    private final ProviderRequestGuard guard = new ProviderRequestGuard(clock, millis -> { },
            Duration.ZERO, 0, 1, Duration.ofSeconds(60));
    private final Instrument stock = new Instrument();

    @Test
    void triesIndependentSourceWhenPrimaryFailsAndRetainsPriceOnlyReplay() {
        CapitalFlowProvider primary = provider("PRIMARY", 1);
        CapitalFlowProvider backup = provider("BACKUP", 2);
        when(primary.fetchReplay(stock)).thenThrow(new ProviderContractException("TIMEOUT", "unavailable", true));
        when(backup.fetchReplay(stock)).thenReturn(new CapitalReplayData(List.of(point("BACKUP")), List.of(), List.of()));

        CapitalReplayData result = gateway(Runnable::run, primary, backup).fetchCapitalReplay(stock);
        assertEquals("BACKUP", result.getPrices().get(0).getProviderCode());
        assertTrue(result.getFlows().isEmpty());
        assertEquals(List.of("REALTIME_FUND_FLOW_UNAVAILABLE"), result.getWarnings());
    }

    @Test
    void keepsPriceAndFlowAvailabilityIndependentAndPreservesProvenance() {
        CapitalFlowProvider prices = provider("PRICE_SOURCE", 1);
        CapitalFlowProvider funds = provider("FLOW_SOURCE", 2);
        when(prices.fetchReplay(stock)).thenReturn(new CapitalReplayData(List.of(point("PRICE_SOURCE")), List.of(), List.of()));
        when(funds.fetchReplay(stock)).thenReturn(new CapitalReplayData(List.of(), List.of(point("FLOW_SOURCE")),
                List.of("INTRADAY_MARKET_UNAVAILABLE")));

        CapitalReplayData result = gateway(Runnable::run, prices, funds).fetchCapitalReplay(stock);
        assertEquals("PRICE_SOURCE", result.getPrices().get(0).getProviderCode());
        assertEquals("FLOW_SOURCE", result.getFlows().get(0).getProviderCode());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void openPrimaryCircuitDoesNotDisableIndependentReplaySource() {
        CapitalFlowProvider primary = provider("PRIMARY", 1);
        CapitalFlowProvider backup = provider("BACKUP", 2);
        assertThrows(ProviderContractException.class, () -> guard.execute(primary, MarketDataCapability.CAPITAL_REPLAY_1M,
                () -> { throw new ProviderContractException("TIMEOUT", "unavailable", true); }));
        when(backup.fetchReplay(stock)).thenReturn(new CapitalReplayData(List.of(point("BACKUP")), List.of(), List.of()));

        assertEquals(1, gateway(Runnable::run, primary, backup).fetchCapitalReplay(stock).getPrices().size());
        verify(primary, never()).fetchReplay(any());
    }

    @Test
    void timeoutFetchingFundsCannotDiscardAlreadyAvailablePrices() {
        CapitalFlowProvider prices = provider("PRICE_SOURCE", 1);
        CapitalFlowProvider funds = provider("SLOW_FLOW", 2);
        when(prices.fetchReplay(stock)).thenReturn(new CapitalReplayData(List.of(point("PRICE_SOURCE")), List.of(), List.of()));
        when(funds.timeout()).thenReturn(Duration.ofMillis(30));
        when(funds.fetchReplay(stock)).thenAnswer(call -> { Thread.sleep(1000); return null; });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CapitalReplayData result = gateway(executor, prices, funds).fetchCapitalReplay(stock);
            assertEquals(1, result.getPrices().size());
            assertTrue(result.getWarnings().contains("REALTIME_FUND_FLOW_UNAVAILABLE"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void emptySourcesReturnExplicitFailureForSnapshotFallback() {
        CapitalFlowProvider source = provider("EMPTY", 1);
        when(source.fetchReplay(stock)).thenReturn(new CapitalReplayData(List.of(), List.of(), List.of()));
        assertEquals("REPLAY_PROVIDER_UNAVAILABLE", assertThrows(ProviderContractException.class,
                () -> gateway(Runnable::run, source).fetchCapitalReplay(stock)).getErrorType());
    }

    private MarketDataGateway gateway(Executor executor, CapitalFlowProvider... providers) {
        stock.setId(6L);
        return new MarketDataGateway(List.of(), List.of(), Arrays.asList(providers),
                new ProviderRoutePolicy(guard), guard, mock(MarketDataSnapshotRepository.class),
                mock(MarketDataRefreshRunRepository.class), new MarketDataSnapshotCodec(new ObjectMapper().findAndRegisterModules()),
                new QuoteQualityValidator(clock), new MarketDataSingleFlight(),
                new MarketDataGatewayProperties(0, 0, 1000), executor, clock);
    }

    private CapitalFlowProvider provider(String code, int priority) {
        CapitalFlowProvider provider = mock(CapitalFlowProvider.class);
        when(provider.providerCode()).thenReturn(code);
        when(provider.providerFamily()).thenReturn(code);
        when(provider.reliabilityFamily()).thenReturn(code);
        when(provider.priority()).thenReturn(priority);
        when(provider.minimumInterval()).thenReturn(Duration.ZERO);
        when(provider.timeout()).thenReturn(Duration.ofSeconds(1));
        when(provider.supports(any(Instrument.class))).thenReturn(true);
        when(provider.supports(MarketDataCapability.CAPITAL_REPLAY_1M)).thenReturn(true);
        return provider;
    }

    private CapitalFlowPoint point(String provider) {
        CapitalFlowPoint point = new CapitalFlowPoint();
        point.setProviderCode(provider);
        point.setObservedAt(LocalDateTime.of(2026, 11, 11, 9, 31));
        point.setPrice(BigDecimal.TEN);
        return point;
    }
}
