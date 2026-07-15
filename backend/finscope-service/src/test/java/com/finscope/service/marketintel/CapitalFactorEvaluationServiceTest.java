package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalBehaviorEvaluation;
import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalHistoryQuality;
import com.finscope.domain.marketintel.CapitalSignalEvaluation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapitalFactorEvaluationServiceTest {

    @Test
    void evaluatesOnlyMaturedEventsAndProducesExploratoryStatistics() {
        List<CapitalFlowPoint> points = dailyPoints(12);
        Collections.reverse(points);
        CapitalFactorEvaluationService service = service(signalOnEveryPrefixFrom(2));

        CapitalBehaviorEvaluation result = service.evaluate(snapshot(points));

        assertEquals("capital-evaluation-v2", result.getEvaluationVersion());
        assertEquals(12, result.getDailySampleCount());
        assertEquals("AVAILABLE", result.getStatus());
        assertEquals(new BigDecimal("1.000000"), result.getCoverageRate());
        assertEquals(new BigDecimal("0.000000"), result.getMissingLossRate());
        CapitalSignalEvaluation fiveDay = evaluation(result, 5);
        assertEquals(6, fiveDay.getSampleCount());
        assertEquals("EXPLORATORY", fiveDay.getEvaluationStatus());
        assertTrue(fiveDay.getAverageReturn().signum() > 0);
        assertEquals(new BigDecimal("1.000000"), fiveDay.getPositiveRate());
        assertEquals("INSUFFICIENT_SAMPLE", fiveDay.getStabilityStatus());
    }

    @Test
    void keepsTheSameHistoricalEventWhenOnlyFutureFlowChanges() {
        LocalDate eventDate = LocalDate.of(2026, 7, 6);
        List<CapitalFlowPoint> baseline = dailyPoints(10);
        List<CapitalFlowPoint> changedFuture = dailyPoints(10);
        changedFuture.stream().filter(point -> point.getDataDate().isAfter(eventDate))
                .forEach(point -> point.setMainNetInflow(new BigDecimal("-999999999")));
        CapitalFactorEvaluationService service = service(signalThrough(eventDate));

        CapitalBehaviorEvaluation first = service.evaluate(snapshot(baseline));
        CapitalBehaviorEvaluation second = service.evaluate(snapshot(changedFuture));

        assertEquals(6, evaluation(first, 3).getSampleCount());
        assertEquals(6, evaluation(second, 3).getSampleCount());
        assertEquals(evaluation(first, 3).getAverageReturn(), evaluation(second, 3).getAverageReturn());
        assertNotEquals(first.getInputFingerprint(), second.getInputFingerprint());
    }

    @Test
    void isOrderIndependentAndDoesNotPublishPercentagesBelowTheSampleGate() {
        List<CapitalFlowPoint> ascending = dailyPoints(7);
        List<CapitalFlowPoint> descending = new ArrayList<CapitalFlowPoint>(ascending);
        descending.sort(Comparator.comparing(CapitalFlowPoint::getObservedAt).reversed());
        CapitalFactorEvaluationService service = service(signalOnlyOn(LocalDate.of(2026, 7, 3)));

        CapitalBehaviorEvaluation first = service.evaluate(snapshot(ascending));
        CapitalBehaviorEvaluation second = service.evaluate(snapshot(descending));
        CapitalSignalEvaluation oneDay = evaluation(first, 1);

        assertEquals(first.getInputFingerprint(), second.getInputFingerprint());
        assertEquals("INSUFFICIENT_DATA", first.getStatus());
        assertEquals("UNTESTED", oneDay.getEvaluationStatus());
        assertNull(oneDay.getAverageReturn());
        assertNull(oneDay.getPositiveRate());
        assertNull(oneDay.getAverageMfe());
        assertNull(oneDay.getAverageMae());
        assertTrue(first.getDataGaps().stream().anyMatch(value -> value.contains("未展示百分比")));
    }

    @Test
    void countsMissingForwardPricesWithoutInventingOutcomes() {
        List<CapitalFlowPoint> points = dailyPoints(8);
        points.get(4).setPrice(null);
        CapitalFactorEvaluationService service = service(signalOnEveryPrefixFrom(2));

        CapitalBehaviorEvaluation result = service.evaluate(snapshot(points));

        assertTrue(result.getMissingLossRate().signum() > 0);
        assertTrue(result.getCoverageRate().compareTo(BigDecimal.ONE) < 0);
        assertTrue(result.getDataGaps().stream().anyMatch(value -> value.contains("价格标签缺失")));
    }

    @Test
    void keepsZeroSampleRowsForSignalsWhoseForwardHorizonsHaveNotMatured() {
        List<CapitalFlowPoint> points = dailyPoints(7);
        CapitalFactorEvaluationService service = service(signalOnlyOn(LocalDate.of(2026, 7, 7)));

        CapitalBehaviorEvaluation result = service.evaluate(snapshot(points));

        assertEquals(3, result.getSignals().size());
        assertTrue(result.getSignals().stream().allMatch(value -> value.getSampleCount() == 0));
        assertTrue(result.getSignals().stream().allMatch(value -> "UNTESTED".equals(value.getEvaluationStatus())));
        assertEquals(Arrays.asList(1, 3, 5), result.getSignals().stream()
                .map(CapitalSignalEvaluation::getHorizonDays).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void comparesSignalReturnsWithTheStocksUnconditionalBaseline() {
        List<CapitalFlowPoint> points = biasedHistory(80);
        Set<LocalDate> eventDates = new LinkedHashSet<LocalDate>();
        for (int index = 10; index <= 60; index += 10) eventDates.add(points.get(index).getDataDate());
        CapitalFactorEvaluationService service = new CapitalFactorEvaluationService(
                signalOnDates(eventDates), new CapitalHistoryQualityGate());

        CapitalBehaviorEvaluation result = service.evaluate(snapshot(points));
        CapitalSignalEvaluation oneDay = evaluation(result, 1);

        assertEquals("RELIABLE", result.getHistoryQualityStatus());
        assertTrue(oneDay.getBaselineAverageReturn().signum() > 0);
        assertTrue(oneDay.getAverageReturn().compareTo(oneDay.getBaselineAverageReturn()) < 0);
        assertTrue(oneDay.getExcessAverageReturn().signum() < 0);
        assertEquals(oneDay.getAverageReturn().subtract(oneDay.getBaselineAverageReturn()),
                oneDay.getExcessAverageReturn());
        assertEquals("BASELINE", oneDay.getDecayStatus());
    }

    @Test
    void withholdsStatisticsWhenHistoricalInputsFailTheQualityGate() {
        List<CapitalFlowPoint> points = dailyPoints(40);
        CapitalFactorEvaluationService service = new CapitalFactorEvaluationService(
                signalOnEveryPrefixFrom(2), new CapitalHistoryQualityGate());

        CapitalBehaviorEvaluation result = service.evaluate(snapshot(points));
        CapitalSignalEvaluation oneDay = evaluation(result, 1);

        assertEquals("DATA_UNRELIABLE", result.getStatus());
        assertEquals("DATA_UNRELIABLE", result.getHistoryQualityStatus());
        assertEquals("UNTESTED", oneDay.getEvaluationStatus());
        assertNull(oneDay.getAverageReturn());
        assertNull(oneDay.getExcessAverageReturn());
        assertTrue(result.getDataGaps().stream().anyMatch(value -> value.contains("至少需要 60")));
    }

    private CapitalSignalEvaluation evaluation(CapitalBehaviorEvaluation result, int horizon) {
        return result.getSignals().stream().filter(item -> item.getHorizonDays() == horizon)
                .findFirst().orElseThrow(() -> new AssertionError("missing horizon " + horizon));
    }

    private CapitalFactorEvaluationService service(CapitalBehaviorSignalService signalService) {
        CapitalHistoryQualityGate relaxed = new CapitalHistoryQualityGate() {
            @Override
            public CapitalHistoryQuality evaluate(List<CapitalFlowPoint> facts, LocalDate asOfDate) {
                CapitalHistoryQuality quality = new CapitalHistoryQuality();
                quality.setStatus("RELIABLE");
                quality.setDailySampleCount(facts == null ? 0 : facts.size());
                quality.setPriceCoverageRate(new BigDecimal("1.000000"));
                quality.setAmountCoverageRate(new BigDecimal("1.000000"));
                quality.setDataGaps(Collections.emptyList());
                return quality;
            }
        };
        return new CapitalFactorEvaluationService(signalService, relaxed);
    }

    private CapitalBehaviorSnapshot snapshot(List<CapitalFlowPoint> points) {
        LocalDateTime asOf = points.stream().map(CapitalFlowPoint::getObservedAt)
                .max(LocalDateTime::compareTo).orElse(LocalDateTime.of(2026, 7, 15, 15, 0));
        CapitalBehaviorSnapshot snapshot = CapitalBehaviorSnapshot.of(7L,
                asOf, points, Collections.emptyList(), "snapshot-fp");
        snapshot.setId(11L);
        return snapshot;
    }

    private List<CapitalFlowPoint> dailyPoints(int count) {
        List<CapitalFlowPoint> result = new ArrayList<CapitalFlowPoint>();
        LocalDate start = LocalDate.of(2026, 7, 1);
        for (int index = 0; index < count; index++) {
            CapitalFlowPoint point = new CapitalFlowPoint();
            point.setId((long) index + 1);
            point.setInstrumentId(7L);
            point.setProviderCode("TEST");
            point.setGranularity("DAY_1");
            point.setDataDate(start.plusDays(index));
            point.setObservedAt(start.plusDays(index).atTime(15, 0));
            point.setPrice(new BigDecimal(10 + index));
            point.setIntervalTradeAmount(new BigDecimal(1000 + index * 100));
            point.setMainNetInflow(new BigDecimal(index % 2 == 0 ? "100" : "-50"));
            point.setCalculationVersion("test-v1");
            point.setPayloadHash("payload-" + index);
            point.setQualityStatus("COMPLETE");
            result.add(point);
        }
        return result;
    }

    private List<CapitalFlowPoint> biasedHistory(int count) {
        List<CapitalFlowPoint> points = dailyPoints(count);
        BigDecimal price = new BigDecimal("10");
        for (int index = 0; index < points.size(); index++) {
            if (index > 0) {
                boolean followsEvent = (index - 1) >= 10 && (index - 1) <= 60 && (index - 1) % 10 == 0;
                price = price.multiply(followsEvent ? new BigDecimal("0.99") : new BigDecimal("1.02"));
            }
            points.get(index).setPrice(price.setScale(8, java.math.RoundingMode.HALF_UP));
        }
        return points;
    }

    private CapitalBehaviorSignalService signalOnEveryPrefixFrom(int minimumSize) {
        return new CapitalBehaviorSignalService() {
            @Override
            public List<CapitalBehaviorSignal> detect(List<CapitalFlowPoint> facts) {
                return facts.size() >= minimumSize ? Collections.singletonList(signal()) : Collections.emptyList();
            }
        };
    }

    private CapitalBehaviorSignalService signalOnlyOn(LocalDate eventDate) {
        return new CapitalBehaviorSignalService() {
            @Override
            public List<CapitalBehaviorSignal> detect(List<CapitalFlowPoint> facts) {
                if (facts.isEmpty() || !eventDate.equals(facts.get(facts.size() - 1).getDataDate())) {
                    return Collections.emptyList();
                }
                return Collections.singletonList(signal());
            }
        };
    }

    private CapitalBehaviorSignalService signalThrough(LocalDate lastEventDate) {
        return new CapitalBehaviorSignalService() {
            @Override
            public List<CapitalBehaviorSignal> detect(List<CapitalFlowPoint> facts) {
                if (facts.isEmpty() || facts.get(facts.size() - 1).getDataDate().isAfter(lastEventDate)) {
                    return Collections.emptyList();
                }
                return Collections.singletonList(signal());
            }
        };
    }

    private CapitalBehaviorSignalService signalOnDates(Set<LocalDate> eventDates) {
        return new CapitalBehaviorSignalService() {
            @Override
            public List<CapitalBehaviorSignal> detect(List<CapitalFlowPoint> facts) {
                if (facts.isEmpty() || !eventDates.contains(facts.get(facts.size() - 1).getDataDate())) {
                    return Collections.emptyList();
                }
                return Collections.singletonList(signal());
            }
        };
    }

    private CapitalBehaviorSignal signal() {
        CapitalBehaviorSignal signal = CapitalBehaviorSignal.of(
                "AMOUNT_EXPANSION_WITH_INFLOW", CapitalBehaviorSignalService.VERSION,
                Collections.emptyList());
        signal.setLabel("放量净流入");
        return signal;
    }
}
