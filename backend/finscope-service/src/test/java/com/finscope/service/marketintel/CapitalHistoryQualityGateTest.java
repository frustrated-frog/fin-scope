package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalHistoryQuality;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapitalHistoryQualityGateTest {
    private final CapitalHistoryQualityGate gate = new CapitalHistoryQualityGate();

    @Test
    void acceptsFreshHistoryWithEnoughPriceAndAmountCoverage() {
        List<CapitalFlowPoint> points = history(80, LocalDate.of(2026, 4, 27));

        CapitalHistoryQuality result = gate.evaluate(points, LocalDate.of(2026, 7, 15));

        assertTrue(result.isReliable());
        assertEquals("RELIABLE", result.getStatus());
        assertEquals(80, result.getDailySampleCount());
        assertEquals(new BigDecimal("1.000000"), result.getPriceCoverageRate());
        assertEquals(new BigDecimal("1.000000"), result.getAmountCoverageRate());
        assertTrue(result.getDataGaps().isEmpty());
    }

    @Test
    void rejectsShortStaleAndLowCoverageHistoryWithExplicitReasons() {
        List<CapitalFlowPoint> points = history(40, LocalDate.of(2026, 1, 1));
        for (int index = 0; index < 5; index++) points.get(index).setPrice(null);
        for (int index = 5; index < 10; index++) points.get(index).setIntervalTradeAmount(null);

        CapitalHistoryQuality result = gate.evaluate(points, LocalDate.of(2026, 7, 15));

        assertFalse(result.isReliable());
        assertEquals("DATA_UNRELIABLE", result.getStatus());
        assertTrue(result.getDataGaps().stream().anyMatch(value -> value.contains("至少需要 60")));
        assertTrue(result.getDataGaps().stream().anyMatch(value -> value.contains("最新历史数据")));
        assertTrue(result.getDataGaps().stream().anyMatch(value -> value.contains("价格覆盖率")));
        assertTrue(result.getDataGaps().stream().anyMatch(value -> value.contains("成交额覆盖率")));
    }

    @Test
    void rejectsDuplicateTradingDatesInsteadOfSilentlyInflatingSamples() {
        List<CapitalFlowPoint> points = history(60, LocalDate.of(2026, 5, 17));
        points.add(points.get(points.size() - 1));

        CapitalHistoryQuality result = gate.evaluate(points, LocalDate.of(2026, 7, 15));

        assertFalse(result.isReliable());
        assertEquals(60, result.getDailySampleCount());
        assertTrue(result.getDataGaps().stream().anyMatch(value -> value.contains("重复交易日")));
    }

    private List<CapitalFlowPoint> history(int count, LocalDate start) {
        List<CapitalFlowPoint> result = new ArrayList<CapitalFlowPoint>();
        for (int index = 0; index < count; index++) {
            CapitalFlowPoint point = new CapitalFlowPoint();
            point.setGranularity("DAY_1");
            point.setDataDate(start.plusDays(index));
            point.setObservedAt(start.plusDays(index).atTime(15, 0));
            point.setPrice(new BigDecimal("10"));
            point.setIntervalTradeAmount(new BigDecimal("1000000"));
            result.add(point);
        }
        return result;
    }
}
