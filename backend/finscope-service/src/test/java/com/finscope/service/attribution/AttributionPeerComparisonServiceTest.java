package com.finscope.service.attribution;

import com.finscope.domain.attribution.AttributionPeerCandidate;
import com.finscope.domain.attribution.AttributionResearchInsights;
import com.finscope.domain.instrument.DailyBarPoint;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketpulse.SectorRotationItem;
import com.finscope.rpc.quote.PythonDailyBarClient;
import com.finscope.service.marketdata.MarketDataGateway;
import com.finscope.service.marketdata.QuoteGatewayResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttributionPeerComparisonServiceTest {
    private AttributionPeerComparisonService service;
    private PythonDailyBarClient bars;
    private MarketDataGateway gateway;
    private Instrument stock;
    private final LocalDate date = LocalDate.of(2026, 9, 18);

    @BeforeEach
    void setup() {
        service = new AttributionPeerComparisonService();
        bars = mock(PythonDailyBarClient.class);
        gateway = mock(MarketDataGateway.class);
        ReflectionTestUtils.setField(service, "dailyBarClient", bars);
        ReflectionTestUtils.setField(service, "marketDataGateway", gateway);
        stock = new Instrument();
        stock.setCode("603618");
        stock.setName("杭电股份");
        when(bars.fetchDailyBars("603618", 250)).thenReturn(series(2));
        when(bars.fetchMarketBenchmark(250)).thenReturn(series(1));
    }

    @Test
    void computesReturnsFromTargetDayOnlyAndIgnoresCurrentQuotesAndDuplicatePeers() {
        Quote identity = new Quote();
        identity.setInstrumentCode("600487");
        identity.setName("亨通光电");
        identity.setChangePct(99D);
        when(gateway.fetchQuotes(anyString(), anyList(), eq(false))).thenReturn(
                new QuoteGatewayResult(List.of(identity), null, null, null, null, null, null, null));
        when(bars.fetchDailyBars("600487", 250)).thenReturn(series(3));
        var sector = new SectorRotationItem();
        sector.setSectorCode("881111");
        sector.setSectorName("通信设备");
        sector.setReturn1d(1.5);
        sector.setReturn5d(5D);
        var result = new AttributionResearchInsights();
        service.capture(result, stock, date, List.of(peer("600487", "亨通光电"), peer("600487", "亨通光电"),
                peer("603618", "杭电股份"), peer("invalid", "错误代码")), sector);
        assertEquals(4, result.getComparisons().size());
        assertEquals(2D, result.getComparisons().get(0).getChangePct());
        assertEquals((Math.pow(1.02, 5) - 1) * 100, result.getComparisons().get(0).getFiveSessionChangePct(), 0.000001);
        assertEquals(3D, result.getComparisons().get(3).getChangePct());
        assertEquals(-1D, result.getComparisons().get(3).getStockRelativePct());
        assertEquals(0.5D, result.getComparisons().get(2).getStockRelativePct());
        assertTrue(result.getComparisonSummary().contains("1 家上涨"));
        assertTrue(result.getComparisonSummary().contains("强于其中 0 家"));
        verify(bars, times(1)).fetchDailyBars("600487", 250);
    }

    @Test
    void neverBorrowsAnotherDateAndDoesNotUseMismatchedIdentity() {
        when(bars.fetchDailyBars("603618", 250)).thenReturn(List.of(bar(date.minusDays(1), 2)));
        Quote wrong = new Quote();
        wrong.setInstrumentCode("600487");
        wrong.setName("其他公司");
        when(gateway.fetchQuotes(anyString(), anyList(), eq(false))).thenReturn(
                new QuoteGatewayResult(List.of(wrong), null, null, null, null, null, null, null));
        var result = new AttributionResearchInsights();
        service.capture(result, stock, date, List.of(peer("600487", "亨通光电")), null);
        assertNull(result.getComparisons().get(0).getChangePct());
        assertNull(result.getComparisons().get(1).getStockRelativePct());
        assertNull(result.getComparisons().get(2).getChangePct());
        assertTrue(result.getComparisons().get(2).getNote().contains("身份"));
        verify(bars, never()).fetchDailyBars("600487", 250);
    }

    @Test
    void partialServiceFailureKeepsAvailableBenchmarkAndMissingValuesAreNotZero() {
        when(bars.fetchDailyBars("603618", 250)).thenThrow(new IllegalStateException("offline"));
        when(gateway.fetchQuotes(anyString(), anyList(), eq(false))).thenThrow(new IllegalStateException("offline"));
        var result = new AttributionResearchInsights();
        service.capture(result, stock, date, List.of(peer("600487", "亨通光电")), null);
        assertNull(result.getComparisons().get(0).getChangePct());
        assertEquals(1D, result.getComparisons().get(1).getChangePct());
        assertEquals(3, result.getComparisons().size());
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void neverUsesAnOlderSectorSnapshotAsTargetDayData() {
        var repository = mock(com.finscope.dao.marketpulse.MarketPulseRepository.class);
        ReflectionTestUtils.setField(service, "marketPulseRepository", repository);
        var workspace = new com.finscope.domain.marketpulse.MarketPulseWorkspace();
        workspace.setBusinessDate(date.minusDays(1));
        workspace.setSectors(List.of(new SectorRotationItem()));
        when(repository.findWorkspace(date)).thenReturn(java.util.Optional.of(workspace));
        assertTrue(service.sectors(date).isEmpty());
        workspace.setBusinessDate(date);
        assertEquals(1, service.sectors(date).size());
        when(repository.findWorkspace(date)).thenThrow(new IllegalStateException("offline"));
        assertTrue(service.sectors(date).isEmpty());
    }

    @Test
    void unsupportedMarketDoesNotGetAnAShareBenchmark() {
        stock.setCode("NVDA");
        var result = new AttributionResearchInsights();
        service.capture(result, stock, date, List.of(), null);
        assertTrue(result.getComparisons().isEmpty());
        assertFalse(result.getWarnings().isEmpty());
        verifyNoInteractions(bars, gateway);
    }

    private List<DailyBarPoint> series(double change) {
        List<DailyBarPoint> result = new ArrayList<>();
        for (int day = 7; day >= 0; day--) {
            result.add(bar(date.minusDays(day), change));
        }
        result.add(bar(date.plusDays(1), 80));
        return result;
    }

    private DailyBarPoint bar(LocalDate date, double change) {
        var bar = new DailyBarPoint();
        bar.setTradeDate(date);
        bar.setChangePct(BigDecimal.valueOf(change));
        return bar;
    }

    private AttributionPeerCandidate peer(String code, String name) {
        var peer = new AttributionPeerCandidate();
        peer.setCode(code);
        peer.setName(name);
        peer.setReason("同有光纤业务，但收入结构不同");
        return peer;
    }
}
