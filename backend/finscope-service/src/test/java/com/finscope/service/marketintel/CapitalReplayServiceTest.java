package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.CapitalReplayData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapitalReplayServiceTest {
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
