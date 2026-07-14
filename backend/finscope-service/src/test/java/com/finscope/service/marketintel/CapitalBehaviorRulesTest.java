package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalRuleExplanation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapitalBehaviorRulesTest {
    @Test
    void aggregatesFiveMinuteWindowsWithoutLosingMetricProvenance() {
        CapitalFlowAggregationService aggregation = new CapitalFlowAggregationService();
        List<CapitalFlowPoint> result = aggregation.aggregate(Arrays.asList(
                point(1, "MINUTE_1", 10, 30, "100", "10", "20"),
                point(2, "MINUTE_1", 10, 31, "110", "12", "22"),
                point(3, "MINUTE_1", 10, 34, "120", "15", "25")), 5);
        assertEquals(1, result.size());
        assertEquals(new BigDecimal("330"), result.get(0).getIntervalTradeAmount());
        assertEquals(new BigDecimal("37"), result.get(0).getMainNetInflow());
        assertEquals(new BigDecimal("25"), result.get(0).getPrice());
        assertEquals("AGGREGATED_5M", result.get(0).getCalculationVersion());
    }

    @Test
    void explainsAmountExpansionAndOutflowInPlainChineseWithMetricRefs() {
        CapitalBehaviorSignalService signals = new CapitalBehaviorSignalService(CapitalSignalPolicy.v1());
        List<CapitalFlowPoint> facts = Arrays.asList(
                point(11, "DAY_1", 15, 0, "100", "20", "100"),
                point(12, "DAY_1", 15, 0, "180", "-30", "98"));
        facts.get(0).setObservedAt(LocalDateTime.of(2026, 7, 11, 15, 0));
        facts.get(1).setObservedAt(LocalDateTime.of(2026, 7, 14, 15, 0));
        List<CapitalBehaviorSignal> detected = signals.detect(facts);
        assertTrue(detected.stream().anyMatch(v -> "AMOUNT_EXPANSION_WITH_OUTFLOW".equals(v.getType())));

        CapitalRuleExplanation explanation = new CapitalRuleExplanationService().explain(facts, detected);
        assertTrue(explanation.getSummary().contains("成交明显放大"));
        assertTrue(explanation.getItems().get(0).getMetricRefs().contains("flow:12:intervalTradeAmount"));
        assertTrue(explanation.getDataGaps().stream().anyMatch(v -> v.contains("Level-2")));
        assertFalse(explanation.getSummary().contains("买入"));
        assertFalse(explanation.getSummary().contains("卖出"));
        assertEquals("capital-rules-v1", explanation.getRuleVersion());
    }

    private CapitalFlowPoint point(long id,String granularity,int hour,int minute,String amount,String flow,String price) {
        CapitalFlowPoint p=new CapitalFlowPoint();p.setId(id);p.setInstrumentId(7L);p.setGranularity(granularity);
        p.setObservedAt(LocalDateTime.of(2026,7,14,hour,minute));p.setIntervalTradeAmount(new BigDecimal(amount));
        p.setMainNetInflow(new BigDecimal(flow));p.setPrice(new BigDecimal(price));p.setQualityStatus("COMPLETE");return p;
    }
}
