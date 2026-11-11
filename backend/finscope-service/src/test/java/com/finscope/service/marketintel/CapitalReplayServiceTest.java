package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.instrument.Instrument;
import com.finscope.dao.marketintel.CapitalFlowRepository;
import com.finscope.rpc.marketintel.CapitalReplayData;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.service.marketdata.MarketDataGateway;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CapitalReplayServiceTest {
    @Test
    void onlineFailureRestoresSavedReplayBeforeFallingBackToSparseCapitalFacts() {
        MarketIntelCapitalService capital = mock(MarketIntelCapitalService.class);
        CapitalFlowRepository flows = mock(CapitalFlowRepository.class);
        MarketDataGateway gateway = mock(MarketDataGateway.class);
        CapitalReplaySnapshotService snapshots = mock(CapitalReplaySnapshotService.class);
        CapitalReplayService service = new CapitalReplayService();
        ReflectionTestUtils.setField(service, "capital", capital);
        ReflectionTestUtils.setField(service, "flowRepository", flows);
        ReflectionTestUtils.setField(service, "marketData", gateway);
        ReflectionTestUtils.setField(service, "snapshots", snapshots);
        Instrument stock = new Instrument();
        stock.setId(40L);
        stock.setMarket("SH");
        when(capital.stock(40L)).thenReturn(stock);
        when(flows.findRecentMinuteVersions(eq(40L), any(), any())).thenReturn(List.of());
        when(gateway.fetchCapitalReplay(stock)).thenThrow(new ProviderContractException("TIMEOUT", "unavailable", true));
        when(snapshots.retain(eq(40L), any(), any())).thenReturn(new CapitalReplayData(
                List.of(point("2026-11-11T09:31", 5297, null, "EASTMONEY_CAPITAL_FLOW")), List.of(),
                List.of("CAPITAL_REPLAY_SNAPSHOT_FALLBACK")));

        CapitalReplayView result = service.view(40L);
        assertEquals(1, result.getSessions().size());
        assertEquals(new BigDecimal("52.97"), result.getSessions().get(0).getMinutes().get(0).getPrice());
        assertTrue(result.getWarnings().contains("CAPITAL_REPLAY_SNAPSHOT_FALLBACK"));
    }

    @Test
    void alignsByExactTradingDateAndMinuteAndKeepsPriceOnlySessions() {
        CapitalFlowPoint earlierPrice = point("2026-11-06T14:30", 907, null, "EASTMONEY_CAPITAL_FLOW");
        CapitalFlowPoint latestPrice = point("2026-11-10T14:30", 1120, null, "EASTMONEY_CAPITAL_FLOW");
        CapitalFlowPoint latestFlow = point("2026-11-10T14:31", null, 50, "EASTMONEY_CAPITAL_FLOW");

        CapitalReplayView result = CapitalReplayService.assemble(
                new CapitalReplayData(List.of(earlierPrice, latestPrice), List.of(latestFlow), List.of()), List.of());

        assertEquals(2, result.getSessions().size());
        CapitalReplayView.Session latest = result.getSessions().get(0);
        assertEquals(latestPrice.getDataDate(), latest.getDate());
        assertNull(latest.getMinutes().get(0).getLargeNet());
        assertNull(latest.getMinutes().get(1).getPrice());
        assertEquals(BigDecimal.valueOf(50), latest.getMinutes().get(1).getLargeNet());
        assertNull(result.getSessions().get(1).getFlowProvider());
    }

    @Test
    void usesSavedFlowOnlyWhenSameProviderHasPreviousMinute() {
        CapitalFlowPoint price = point("2026-11-06T14:30", 907, null, "EASTMONEY_CAPITAL_FLOW");
        List<CapitalFlowPoint> stored = List.of(
                point("2026-11-06T14:29", null, 100, "EASTMONEY_DIRECT"),
                point("2026-11-06T14:30", null, 200, "EASTMONEY_DIRECT"),
                point("2026-11-06T14:32", null, 400, "EASTMONEY_DIRECT"));

        CapitalReplayView.Session session = CapitalReplayService.assemble(
                new CapitalReplayData(List.of(price), List.of(), List.of()), stored).getSessions().get(0);

        assertEquals("EASTMONEY_DIRECT", session.getFlowProvider());
        assertEquals(1, session.getMinutes().size());
        assertEquals(LocalTime.of(14, 30), session.getMinutes().get(0).getTime());
        assertEquals(BigDecimal.valueOf(200), session.getMinutes().get(0).getLargeNet());
    }

    @Test
    void omitsSessionsWhenSavedFlowHasNoTrustworthyMinute() {
        CapitalReplayView result = CapitalReplayService.assemble(
                new CapitalReplayData(List.of(), List.of(), List.of()),
                List.of(point("2026-11-06T14:30", null, 100, "EASTMONEY_DIRECT")));

        assertTrue(result.getSessions().isEmpty());
    }

    private CapitalFlowPoint point(String timestamp, Integer price, Integer net, String provider) {
        LocalDateTime time = LocalDateTime.parse(timestamp);
        CapitalFlowPoint point = new CapitalFlowPoint();
        point.setDataDate(time.toLocalDate());
        point.setObservedAt(time);
        point.setRetrievedAt(time.plusMinutes(1));
        point.setProviderCode(provider);
        point.setPrice(price == null ? null : BigDecimal.valueOf(price, 2));
        point.setLargeNetInflow(net == null ? null : BigDecimal.valueOf(net));
        point.setSuperLargeNetInflow(net == null ? null : BigDecimal.ZERO);
        return point;
    }
}
