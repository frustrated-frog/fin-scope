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

    @Test
    void factorDefinitionsAndObservationsDefensivelyCopyEvidence() {
        List<String> fields = new ArrayList<String>();
        fields.add("intervalTradeAmount");
        CapitalFactorDefinition definition = CapitalFactorDefinition.builder("AMOUNT_RATIO_5D", "5日量能比")
                .category("VOLUME")
                .description("当前成交额相对近5日均值")
                .expressionKind(CapitalFactorDefinition.ExpressionKind.DECLARATIVE)
                .canonicalFormula("REF(amount) / MEAN(amount, 5)")
                .calculationKey("AMOUNT_RATIO_5D")
                .requiredFields(fields)
                .window("5d")
                .minimumSamples(2)
                .sourceType(CapitalFactorDefinition.SourceType.QLIB)
                .sourceRef("Qlib Alpha158 window features")
                .adaptationType(CapitalFactorDefinition.AdaptationType.ADAPTED)
                .calculationVersion("capital-factor-v1")
                .evaluationStatus(CapitalFactorDefinition.EvaluationStatus.UNTESTED)
                .admissionStatus(CapitalFactorDefinition.AdmissionStatus.PUBLISHED)
                .interpretationBoundary("只描述相对量能，不预测收益")
                .build();
        fields.clear();

        CapitalFactorObservation observation = new CapitalFactorObservation();
        observation.setFactorCode(definition.getCode());
        observation.setObservedAt(LocalDateTime.of(2026, 7, 14, 15, 0));
        observation.setMetricRefs(Collections.singletonList("flow:101:intervalTradeAmount"));

        assertEquals(1, definition.getRequiredFields().size());
        assertEquals("factor:AMOUNT_RATIO_5D:2026-07-14T15:00", observation.factorRef());
        assertThrows(UnsupportedOperationException.class,
                () -> observation.getMetricRefs().add("flow:102:intervalTradeAmount"));
    }
}
