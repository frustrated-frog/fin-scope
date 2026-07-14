package com.finscope.domain.marketintel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapitalMarketIntelContractTest {

    @Test
    void snapshotDefensivelyCopiesFactsAndSignals() {
        CapitalFlowPoint point = new CapitalFlowPoint();
        point.setId(101L);
        point.setInstrumentId(7L);
        point.setGranularity("MINUTE_1");
        point.setObservedAt(LocalDateTime.of(2026, 7, 14, 10, 30));
        point.setMainNetInflow(new BigDecimal("18000000"));

        List<CapitalFlowPoint> facts = new ArrayList<CapitalFlowPoint>();
        facts.add(point);
        List<CapitalBehaviorSignal> signals = new ArrayList<CapitalBehaviorSignal>();
        signals.add(CapitalBehaviorSignal.of("PRICE_FLOW_DIVERGENCE", "capital-signal-v1",
                Collections.singletonList("flow:101:mainNetInflow")));

        CapitalBehaviorSnapshot snapshot = CapitalBehaviorSnapshot.of(7L,
                LocalDateTime.of(2026, 7, 14, 10, 30), facts, signals, "fingerprint-1");
        facts.clear();
        signals.clear();

        assertEquals(1, snapshot.getFacts().size());
        assertEquals("flow:101:mainNetInflow", snapshot.getSignals().get(0).getMetricRefs().get(0));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getFacts().add(new CapitalFlowPoint()));
    }

    @Test
    void refreshStatesExposeTerminalSemantics() {
        assertEquals(true, MarketIntelRefreshRun.Status.PARTIAL.isTerminal());
        assertEquals(false, MarketIntelRefreshStep.Status.RUNNING.isTerminal());
        assertEquals(true, MarketIntelRefreshStep.Status.EMPTY.isTerminal());
    }
}
