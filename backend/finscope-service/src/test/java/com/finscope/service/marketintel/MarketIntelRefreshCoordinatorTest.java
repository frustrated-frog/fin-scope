package com.finscope.service.marketintel;

import com.finscope.dao.marketintel.CapitalBehaviorSnapshotRepository;
import com.finscope.dao.marketintel.CapitalFlowRepository;
import com.finscope.dao.marketintel.CapitalInterpretationRepository;
import com.finscope.dao.marketintel.MarketIntelRefreshRunRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.MarketIntelRefreshRun;
import com.finscope.domain.marketintel.MarketIntelRefreshStep;
import com.finscope.rpc.marketintel.CapitalFlowData;
import com.finscope.service.marketdata.CapitalFlowGatewayResult;
import com.finscope.service.marketdata.MarketDataGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketIntelRefreshCoordinatorTest {
    private final MarketIntelCapitalService capital = mock(MarketIntelCapitalService.class);
    private final MarketDataGateway gateway = mock(MarketDataGateway.class);
    private final CapitalFlowRepository flows = mock(CapitalFlowRepository.class);
    private final CapitalBehaviorSignalService signalService = mock(CapitalBehaviorSignalService.class);
    private final CapitalBehaviorSnapshotFactory snapshotFactory = mock(CapitalBehaviorSnapshotFactory.class);
    private final CapitalBehaviorSnapshotRepository snapshots = mock(CapitalBehaviorSnapshotRepository.class);
    private final CapitalRuleExplanationService ruleService = mock(CapitalRuleExplanationService.class);
    private final CapitalInterpretationRepository interpretations = mock(CapitalInterpretationRepository.class);
    private final CapitalFactAssembler facts = mock(CapitalFactAssembler.class);
    private final MarketIntelRefreshRunRepository runs = mock(MarketIntelRefreshRunRepository.class);
    private final MarketIntelRefreshCoordinator coordinator = new MarketIntelRefreshCoordinator(
            capital, gateway, flows, signalService, snapshotFactory, snapshots,
            ruleService, interpretations, facts, runs);
    private Instrument instrument;
    private MarketIntelRefreshRun run;

    @BeforeEach
    void setUp() {
        instrument = new Instrument();
        instrument.setId(7L);
        instrument.setType("STOCK");
        instrument.setMarket("SH");
        instrument.setCode("600519");
        run = new MarketIntelRefreshRun();
        run.setId(11L);
        run.setInstrumentId(7L);
        MarketIntelRefreshStep step = new MarketIntelRefreshStep();
        step.setId(19L);
        when(runs.createStep(eq(11L), eq("CAPITAL_FLOW"), any(String.class), eq(1))).thenReturn(step);
    }

    @Test
    void allLiveProvidersFailButExistingSnapshotMakesRefreshPartialNotFailed() {
        when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class))).thenReturn(
                CapitalFlowGatewayResult.unavailable("EASTMONEY_CAPITAL_FLOW",
                        "资金源均不可用，保留上一份资金快照", "r-9"));
        when(snapshots.findLatest(7L)).thenReturn(Optional.of(new CapitalBehaviorSnapshot()));

        coordinator.refresh(run, instrument);

        verify(runs).finishRun(11L, MarketIntelRefreshRun.Status.PARTIAL, 0, 0);
        verify(runs).updateStep(19L, MarketIntelRefreshStep.Status.SKIPPED, 0,
                "STALE_FALLBACK", "资金源均不可用，保留上一份资金快照");
        verify(flows, never()).saveAll(anyList());
    }

    @Test
    void coordinatorDelegatesProviderSelectionToGateway() {
        CapitalFlowData empty = new CapitalFlowData(Collections.emptyList(), Collections.emptyList(),
                BigDecimal.ZERO, BigDecimal.ZERO, Collections.emptyList(), "EASTMONEY_CAPITAL_FLOW");
        when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class)))
                .thenReturn(CapitalFlowGatewayResult.freshPrimary(
                        "EASTMONEY_CAPITAL_FLOW", empty, null, "r-10"));

        coordinator.refresh(run, instrument);

        verify(gateway).fetchCapitalFlow(eq(instrument), any(LocalDate.class));
        verify(runs).updateStep(19L, MarketIntelRefreshStep.Status.EMPTY, 0, null, null);
        verify(runs, never()).finishRun(anyLong(), eq(MarketIntelRefreshRun.Status.FAILED), anyInt(), anyInt());
    }
}
