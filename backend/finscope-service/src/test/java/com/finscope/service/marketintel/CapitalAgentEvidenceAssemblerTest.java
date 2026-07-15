package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalAgentEvidencePacket;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalRuleExplanation;
import com.finscope.service.marketintel.factor.CapitalFactorEngine;
import com.finscope.service.marketintel.factor.CapitalFactorRegistry;
import com.finscope.service.quant.factor.TimeSeriesFactorOperators;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapitalAgentEvidenceAssemblerTest {
    @Test
    void buildsAnImmutableStableAndHumanReadableEvidencePacket() {
        CapitalFactorEngine factors = new CapitalFactorEngine(new CapitalFactorRegistry(), new TimeSeriesFactorOperators());
        CapitalBehaviorSignalService signals = new CapitalBehaviorSignalService(CapitalSignalPolicy.v2(), factors);
        CapitalAgentEvidenceAssembler assembler = new CapitalAgentEvidenceAssembler(
                factors, signals, new CapitalMetricCatalog());
        CapitalBehaviorSnapshot snapshot = snapshot();

        CapitalAgentEvidencePacket first = assembler.assemble(snapshot, rules());
        CapitalAgentEvidencePacket second = assembler.assemble(snapshot, rules());

        assertEquals(first.getEvidenceFingerprint(), second.getEvidenceFingerprint());
        assertEquals("capital-factor-v1", first.getFactorVersion());
        assertEquals("capital-signal-v2", first.getSignalVersion());
        assertTrue(first.getRawMetrics().stream().allMatch(item -> item.getLabel() != null));
        assertTrue(first.getRawMetrics().stream().noneMatch(item -> item.getLabel().startsWith("flow:")));
        assertTrue(first.getAllowedHypotheses().contains("ORDER_SPLITTING"));
        assertTrue(first.getCoverageDimensions().size() >= 3);
        assertTrue(first.isSufficientCoverage());
        assertFalse(first.getWatchConditions().isEmpty());
        assertFalse(first.getDataGaps().isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> first.getFactorObservations().clear());
    }

    private CapitalBehaviorSnapshot snapshot() {
        List<CapitalFlowPoint> facts = new ArrayList<CapitalFlowPoint>();
        for (int i = 0; i < 6; i++) {
            CapitalFlowPoint point = point(10 + i, "DAY_1", LocalDateTime.of(2026, 7, 7 + i, 15, 0),
                    String.valueOf(100 + i), String.valueOf(100 + i * 20), String.valueOf(i % 2 == 0 ? 20 : -10));
            point.setTurnoverRate(new BigDecimal(String.valueOf(i + 1)));
            point.setVolumeRatio(new BigDecimal("1.4"));
            point.setSuperLargeNetInflow(new BigDecimal("20"));
            point.setLargeNetInflow(new BigDecimal("10"));
            point.setMediumNetInflow(new BigDecimal("-5"));
            point.setSmallNetInflow(new BigDecimal("-8"));
            facts.add(point);
        }
        facts.add(point(101, "MINUTE_5", LocalDateTime.of(2026, 7, 14, 9, 30), "106", "30", "8"));
        facts.add(point(102, "MINUTE_5", LocalDateTime.of(2026, 7, 14, 9, 35), "106", "40", "-4"));
        facts.add(point(103, "MINUTE_5", LocalDateTime.of(2026, 7, 14, 10, 5), "107", "50", "5"));
        facts.add(point(104, "MINUTE_5", LocalDateTime.of(2026, 7, 14, 10, 10), "107", "60", "12"));
        CapitalBehaviorSnapshot snapshot = CapitalBehaviorSnapshot.of(7L, LocalDateTime.of(2026, 7, 14, 10, 10),
                facts, Collections.emptyList(), "snapshot-fingerprint");
        snapshot.setId(77L);
        snapshot.setQualityStatus("COMPLETE");
        return snapshot;
    }

    private CapitalFlowPoint point(long id, String granularity, LocalDateTime at,
                                   String price, String amount, String flow) {
        CapitalFlowPoint value = new CapitalFlowPoint();
        value.setId(id);
        value.setInstrumentId(7L);
        value.setProviderCode("TEST");
        value.setGranularity(granularity);
        value.setDataDate(at.toLocalDate());
        value.setObservedAt(at);
        value.setPrice(new BigDecimal(price));
        value.setIntervalTradeAmount(new BigDecimal(amount));
        value.setMainNetInflow(new BigDecimal(flow));
        value.setTradeVolume(new BigDecimal("1000"));
        value.setQualityStatus("COMPLETE");
        return value;
    }

    private CapitalRuleExplanation rules() {
        CapitalRuleExplanation value = new CapitalRuleExplanation();
        value.setRuleVersion("capital-rules-v2");
        value.setSummary("规则摘要");
        value.setItems(Collections.emptyList());
        value.setDataGaps(Collections.singletonList("缺少 Level-2，订单结构只能作为代理。"));
        return value;
    }
}
