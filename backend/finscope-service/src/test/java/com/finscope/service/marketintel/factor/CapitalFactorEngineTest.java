package com.finscope.service.marketintel.factor;

import com.finscope.domain.marketintel.CapitalFactorObservation;
import com.finscope.domain.marketintel.CapitalFactorResult;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.service.quant.factor.TimeSeriesFactorOperators;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapitalFactorEngineTest {
    private final CapitalFactorEngine engine = new CapitalFactorEngine(
            new CapitalFactorRegistry(), new TimeSeriesFactorOperators());

    @Test
    void computesFiveFactorFamiliesWithStableProvenanceRegardlessOfInputOrder() {
        List<CapitalFlowPoint> facts = facts();
        List<CapitalFlowPoint> reversed = new ArrayList<CapitalFlowPoint>(facts);
        Collections.reverse(reversed);

        CapitalFactorResult ordered = engine.calculate(facts);
        CapitalFactorResult unordered = engine.calculate(reversed);

        assertEquals(signature(ordered), signature(unordered));
        assertEquals("capital-factor-v1", ordered.getFactorVersion());
        assertDecimal("2.272727", factor(ordered, "AMOUNT_RATIO_5D").getValue());
        assertDecimal("0.200000", factor(ordered, "MAIN_FLOW_SHARE").getValue());
        assertDecimal("120.000000", factor(ordered, "BIG_ORDER_NET").getValue());
        assertDecimal("-50.000000", factor(ordered, "SMALL_MID_ORDER_NET").getValue());
        assertEquals("DIVERGENT", factor(ordered, "BIG_SMALL_DIVERGENCE").getState());
        assertEquals("ALIGNED", factor(ordered, "PRICE_FLOW_ALIGNMENT").getState());
        assertTrue(factor(ordered, "INTRADAY_FLOW_REVERSALS").getValue().intValue() >= 2);
        assertTrue(factor(ordered, "LATE_SESSION_FLOW_SHARE").getMetricRefs().stream()
                .allMatch(ref -> ref.startsWith("flow:")));
        assertEquals("10:10", factor(ordered, "PEAK_INFLOW_BUCKET").getState());
    }

    @Test
    void reportsMissingSamplesAndNeverDividesByZero() {
        CapitalFlowPoint point = daily(1, LocalDate.of(2026, 7, 14), "100", "0", "0");
        point.setTurnoverRate(null);
        CapitalFactorResult result = engine.calculate(Collections.singletonList(point));

        assertFalse(result.find("MAIN_FLOW_SHARE").isPresent());
        assertTrue(result.getDataGaps().stream().anyMatch(gap -> gap.contains("主力净额占比")));
        assertTrue(result.getDataGaps().stream().anyMatch(gap -> gap.contains("换手率历史分位")));
    }

    @Test
    void omitsDirectionalPeakWhenIntradayFlowNeverMovesInThatDirection() {
        CapitalFactorResult onlyInflows = engine.calculate(Arrays.asList(
                minute(101, 9, 30, "10"),
                minute(102, 9, 35, "20")
        ));
        CapitalFactorResult onlyOutflows = engine.calculate(Arrays.asList(
                minute(103, 9, 40, "-10"),
                minute(104, 9, 45, "-20")
        ));

        assertTrue(onlyInflows.find("PEAK_INFLOW_BUCKET").isPresent());
        assertFalse(onlyInflows.find("PEAK_OUTFLOW_BUCKET").isPresent());
        assertFalse(onlyOutflows.find("PEAK_INFLOW_BUCKET").isPresent());
        assertTrue(onlyOutflows.find("PEAK_OUTFLOW_BUCKET").isPresent());
    }

    private List<String> signature(CapitalFactorResult result) {
        return result.getObservations().stream()
                .map(item -> item.getFactorCode() + "=" + item.getValue() + "=" + item.getState())
                .collect(Collectors.toList());
    }

    private CapitalFactorObservation factor(CapitalFactorResult result, String code) {
        return result.find(code).orElseThrow(() -> new AssertionError("missing " + code));
    }

    private List<CapitalFlowPoint> facts() {
        List<CapitalFlowPoint> values = new ArrayList<CapitalFlowPoint>();
        for (int i = 0; i < 6; i++) {
            CapitalFlowPoint point = daily(10 + i, LocalDate.of(2026, 7, 7 + i),
                    String.valueOf(100 + i * 5), String.valueOf(50 + i * 10), String.valueOf(5 + i));
            point.setTurnoverRate(new BigDecimal(String.valueOf(1 + i)));
            point.setVolumeRatio(new BigDecimal("1.60"));
            point.setSuperLargeNetInflow(new BigDecimal(i == 5 ? "80" : "20"));
            point.setLargeNetInflow(new BigDecimal(i == 5 ? "40" : "10"));
            point.setMediumNetInflow(new BigDecimal(i == 5 ? "-20" : "-5"));
            point.setSmallNetInflow(new BigDecimal(i == 5 ? "-30" : "-5"));
            values.add(point);
        }
        CapitalFlowPoint latest = values.get(values.size() - 1);
        latest.setIntervalTradeAmount(new BigDecimal("250"));
        latest.setMainNetInflow(new BigDecimal("50"));
        values.add(minute(101, 9, 30, "10"));
        values.add(minute(102, 9, 35, "-20"));
        values.add(minute(103, 10, 5, "-10"));
        values.add(minute(104, 10, 10, "40"));
        return values;
    }

    private CapitalFlowPoint daily(long id, LocalDate date, String price, String amount, String flow) {
        CapitalFlowPoint point = new CapitalFlowPoint();
        point.setId(id);
        point.setInstrumentId(7L);
        point.setProviderCode("TEST");
        point.setGranularity("DAY_1");
        point.setDataDate(date);
        point.setObservedAt(date.atTime(15, 0));
        point.setPrice(new BigDecimal(price));
        point.setIntervalTradeAmount(new BigDecimal(amount));
        point.setMainNetInflow(new BigDecimal(flow));
        point.setQualityStatus("COMPLETE");
        return point;
    }

    private CapitalFlowPoint minute(long id, int hour, int minute, String flow) {
        CapitalFlowPoint point = daily(id, LocalDate.of(2026, 7, 14), "130", "100", flow);
        point.setGranularity("MINUTE_5");
        point.setObservedAt(LocalDateTime.of(2026, 7, 14, hour, minute));
        return point;
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected), actual);
    }
}
