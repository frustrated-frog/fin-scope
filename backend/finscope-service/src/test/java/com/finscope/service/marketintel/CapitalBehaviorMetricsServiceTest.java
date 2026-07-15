package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CapitalBehaviorMetricsServiceTest {
    private final CapitalBehaviorMetricsService service = new CapitalBehaviorMetricsService();

    @Test
    void derivesLatestMarketMetricsAndSignedMainFlowShare() {
        CapitalFlowPoint latest = point(3, "DAY_1", 14, 15, 0, "1800000000", "-300000000");
        latest.setTradeVolume(new BigDecimal("1210000"));
        latest.setTurnoverRate(new BigDecimal("3.21"));
        latest.setVolumeRatio(new BigDecimal("1.67"));

        CapitalBehaviorMetrics metrics = service.derive(Collections.emptyList(), Collections.singletonList(latest), Collections.emptyList());

        assertEquals(new BigDecimal("1800000000"), metrics.getLatest().getTradeAmount());
        assertEquals(new BigDecimal("1210000"), metrics.getLatest().getTradeVolume());
        assertEquals(new BigDecimal("3.21"), metrics.getLatest().getTurnoverRate());
        assertEquals(new BigDecimal("1.67"), metrics.getLatest().getVolumeRatio());
        assertEquals(new BigDecimal("-16.666667"), metrics.getLatest().getMainNetInflowSharePct());
    }

    @Test
    void derivesCurrentIntradayAndDailyFlowStreaks() {
        CapitalBehaviorMetrics metrics = service.derive(
                Arrays.asList(
                        point(1, "MINUTE_5", 14, 10, 0, "100", "10"),
                        point(2, "MINUTE_5", 14, 10, 5, "100", "20"),
                        point(3, "MINUTE_5", 14, 10, 10, "100", "30")),
                Arrays.asList(
                        point(4, "DAY_1", 10, 15, 0, "100", "20"),
                        point(5, "DAY_1", 11, 15, 0, "100", "-10"),
                        point(6, "DAY_1", 14, 15, 0, "100", "-30")),
                Collections.emptyList());

        assertEquals("INFLOW", metrics.getIntradayStreak().getDirection());
        assertEquals(3, metrics.getIntradayStreak().getPeriods());
        assertEquals("OUTFLOW", metrics.getDailyStreak().getDirection());
        assertEquals(2, metrics.getDailyStreak().getPeriods());
    }

    @Test
    void prefersAlignedSameDayDailyTotalsAndUsesLatestMinuteAsObservationTime() {
        CapitalFlowPoint first = point(1, "MINUTE_5", 14, 10, 0, "100", "10");
        first.setTradeVolume(new BigDecimal("20"));
        first.setCumulativeTradeAmount(new BigDecimal("100"));
        CapitalFlowPoint latest = point(2, "MINUTE_5", 14, 10, 5, "150", "20");
        latest.setTradeVolume(new BigDecimal("30"));
        latest.setCumulativeTradeAmount(new BigDecimal("250"));
        CapitalFlowPoint daily = point(3, "DAY_1", 14, 15, 0, "999", "999");
        daily.setTurnoverRate(new BigDecimal("3.21"));
        daily.setVolumeRatio(new BigDecimal("1.67"));

        daily.setTradeVolume(new BigDecimal("88"));

        CapitalBehaviorMetrics metrics = service.derive(Arrays.asList(first, latest), Collections.singletonList(daily), Collections.emptyList());

        assertEquals(latest.getObservedAt(), metrics.getLatest().getObservedAt());
        assertEquals(new BigDecimal("999"), metrics.getLatest().getTradeAmount());
        assertEquals(new BigDecimal("88"), metrics.getLatest().getTradeVolume());
        assertEquals(new BigDecimal("999"), metrics.getLatest().getMainNetInflow());
        assertEquals(new BigDecimal("100.000000"), metrics.getLatest().getMainNetInflowSharePct());
        assertEquals(new BigDecimal("3.21"), metrics.getLatest().getTurnoverRate());
    }

    @Test
    void fallsBackToOneConsistentIntradayWindowWhenDailyMarketContextIsMissing() {
        CapitalFlowPoint first = point(1, "MINUTE_1", 14, 10, 0, "100", "10");
        first.setTradeVolume(new BigDecimal("20"));
        CapitalFlowPoint latest = point(2, "MINUTE_1", 14, 10, 1, "150", "20");
        latest.setTradeVolume(new BigDecimal("30"));
        CapitalFlowPoint partialDaily = point(3, "DAY_1", 14, 15, 0, "0", "999");
        partialDaily.setIntervalTradeAmount(null);

        CapitalBehaviorMetrics metrics = service.derive(Arrays.asList(first, latest), Collections.singletonList(partialDaily), Collections.emptyList());

        assertEquals(new BigDecimal("250"), metrics.getLatest().getTradeAmount());
        assertEquals(new BigDecimal("50"), metrics.getLatest().getTradeVolume());
        assertEquals(new BigDecimal("30"), metrics.getLatest().getMainNetInflow());
        assertEquals(new BigDecimal("12.000000"), metrics.getLatest().getMainNetInflowSharePct());
    }

    @Test
    void intradayStreakStopsAtDateAndMissingBucketBoundaries() {
        CapitalBehaviorMetrics metrics = service.derive(Arrays.asList(
                point(1, "MINUTE_1", 13, 14, 59, "100", "10"),
                point(2, "MINUTE_1", 14, 10, 0, "100", "20"),
                point(3, "MINUTE_1", 14, 10, 2, "100", "30")), Collections.emptyList(), Collections.emptyList());

        assertEquals("MINUTE_1", metrics.getIntradayStreak().getGranularity());
        assertEquals(1, metrics.getIntradayStreak().getPeriods());
        assertEquals(LocalDateTime.of(2026, 7, 14, 10, 2), metrics.getIntradayStreak().getSince());
    }

    @Test
    void intradayStreakTreatsTheMiddayMarketBreakAsAdjacent() {
        CapitalBehaviorMetrics metrics = service.derive(Arrays.asList(
                point(1, "MINUTE_1", 14, 11, 30, "100", "10"),
                point(2, "MINUTE_1", 14, 13, 0, "100", "20")),
                Collections.emptyList(), Collections.emptyList());

        assertEquals(2, metrics.getIntradayStreak().getPeriods());
    }

    @Test
    void exposesVersionedObjectiveTagsWithoutIntentLanguage() {
        CapitalFlowPoint point = point(9, "DAY_1", 14, 15, 0, "180", "-30");
        CapitalBehaviorSignal signal = CapitalBehaviorSignal.of(
                "AMOUNT_EXPANSION_WITH_OUTFLOW",
                "capital-signal-v1",
                Arrays.asList("flow:9:intervalTradeAmount", "flow:9:mainNetInflow"));
        signal.setWindow("2d");

        CapitalBehaviorMetrics metrics = service.derive(Collections.emptyList(), Collections.singletonList(point), Collections.singletonList(signal));

        assertEquals(1, metrics.getObjectiveTags().size());
        assertEquals("放量净流出", metrics.getObjectiveTags().get(0).getLabel());
        assertEquals("capital-signal-v1", metrics.getObjectiveTags().get(0).getVersion());
    }

    @Test
    void ignoresObjectiveTagsFromUnsupportedRuleVersions() {
        CapitalBehaviorSignal signal = CapitalBehaviorSignal.of(
                "AMOUNT_EXPANSION_WITH_OUTFLOW", "capital-signal-v999", Collections.emptyList());

        CapitalBehaviorMetrics metrics = service.derive(Collections.emptyList(), Collections.emptyList(), Collections.singletonList(signal));

        assertEquals(0, metrics.getObjectiveTags().size());
    }

    private CapitalFlowPoint point(long id, String granularity, int day, int hour, int minute, String amount, String flow) {
        CapitalFlowPoint point = new CapitalFlowPoint();
        point.setId(id);
        point.setInstrumentId(7L);
        point.setGranularity(granularity);
        point.setObservedAt(LocalDateTime.of(2026, 7, day, hour, minute));
        point.setIntervalTradeAmount(new BigDecimal(amount));
        point.setMainNetInflow(new BigDecimal(flow));
        return point;
    }
}
