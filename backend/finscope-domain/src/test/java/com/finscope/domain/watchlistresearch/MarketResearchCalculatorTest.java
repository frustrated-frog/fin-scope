package com.finscope.domain.watchlistresearch;

import com.finscope.domain.instrument.DailyBarHistory;
import com.finscope.domain.instrument.DailyBarPoint;
import com.finscope.domain.instrument.WatchlistItem;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.IntToDoubleFunction;
import static org.junit.jupiter.api.Assertions.*;

class MarketResearchCalculatorTest {
    @Test
    void fitsOnlyEarlierReturnsAndDetectsAnEvaluationPeriodChange() {
        DailyBarHistory benchmark = prices(160, index -> market(index));
        DailyBarHistory stock = prices(160, index -> 0.0005 + 1.7 * market(index)
                + (index > 140 ? 0.004 : 0));

        StockStrengthAnalysis result = MarketResearchCalculator.strength(stock("1"), stock, benchmark);

        assertEquals(1.7, result.getBeta(), 1e-10);
        assertEquals(1.0, result.getFitQuality(), 1e-10);
        assertEquals(120, result.getTrainingSamples());
        assertEquals(20, result.getPositiveResidualDays());
        assertTrue(result.getExcessReturnPp() > 7.0);
        assertEquals(result.getTrainingThrough(), result.getEvaluatedFrom());
        assertTrue(result.getGaps().isEmpty());
        WatchlistResearchSnapshot snapshot = new WatchlistResearchSnapshot();
        snapshot.getStrengths().add(result);
        assertEquals(1, WatchlistResearchRules.findings(snapshot).size());
    }

    @Test
    void refusesLatestGapsAndDoesNotMixMultiDayAndDailyReturns() {
        DailyBarHistory stock = prices(100, MarketResearchCalculatorTest::market);
        stock.getBars().remove(95);
        StockStrengthAnalysis result = MarketResearchCalculator.strength(stock("1"), stock,
                prices(100, MarketResearchCalculatorTest::market));
        assertNull(result.getExcessReturnPp());
        assertFalse(result.getGaps().isEmpty());
    }

    @Test
    void excludesGapsFromTrainingWithoutJoiningAcrossDifferentIntervals() {
        DailyBarHistory stock = prices(160, index -> 1.5 * market(index));
        stock.getBars().remove(50);
        StockStrengthAnalysis result = MarketResearchCalculator.strength(stock("1"), stock,
                prices(160, MarketResearchCalculatorTest::market));
        assertEquals(1.5, result.getBeta(), 1e-10);
        assertEquals(0.0, result.getExcessReturnPp(), 1e-9);
    }

    @Test
    void refusesUnverifiedAdjustmentIncompleteBarsShortAndFlatSeries() {
        DailyBarHistory benchmark = prices(100, MarketResearchCalculatorTest::market);
        DailyBarHistory stock = prices(100, MarketResearchCalculatorTest::market);
        stock.getBars().get(10).setAdjustment("NONE");
        assertNull(MarketResearchCalculator.strength(stock("1"), stock, benchmark).getBeta());
        stock.getBars().get(10).setAdjustment("QFQ");
        stock.getBars().get(10).setIsComplete(false);
        assertNull(MarketResearchCalculator.strength(stock("1"), stock, benchmark).getBeta());
        assertNull(MarketResearchCalculator.strength(stock("1"), prices(60, index -> .01), benchmark).getBeta());
        assertNull(MarketResearchCalculator.strength(stock("1"), benchmark, prices(100, index -> 0)).getBeta());
    }

    @Test
    void separatesMarketCoMovementFromSharedResidualMovement() {
        DailyBarHistory benchmark = prices(160, MarketResearchCalculatorTest::market);
        DailyBarHistory a = prices(160, index -> market(index) + Math.sin(index * .31) * .002);
        DailyBarHistory b = prices(160, index -> market(index) + Math.cos(index * .71) * .002);
        DailyBarHistory c = prices(160, index -> market(index) + Math.sin(index * .31) * .002
                + Math.cos(index * .71) * .0002);
        List<StockRelationship> pairs = MarketResearchCalculator.relationships(
                List.of(stock("a"), stock("b"), stock("c")), Map.of("a", a, "b", b, "c", c), benchmark);
        StockRelationship unrelated = pairs.stream().filter(pair -> pair.getRightCode().equals("b")).findFirst().orElseThrow();
        StockRelationship linked = pairs.stream().filter(pair -> pair.getLeftCode().equals("a")
                && pair.getRightCode().equals("c")).findFirst().orElseThrow();
        assertTrue(unrelated.getCorrelation() > .75);
        assertTrue(unrelated.getResidualCorrelation() < .5);
        assertFalse(unrelated.isStrong());
        assertTrue(linked.isStrong());
        assertTrue(linked.getResidualCorrelation() > .95);
        assertEquals(60, linked.getSamples());
        assertNotNull(linked.getDownsideCorrelation());
    }

    @Test
    void staleFallbackRetainsDatedMetricsButCannotGenerateFreshSignalsOrLinks() {
        DailyBarHistory benchmark = prices(160, MarketResearchCalculatorTest::market);
        benchmark.setQualityStatus("STALE_FALLBACK");
        DailyBarHistory own = prices(160, index -> market(index) + (index > 140 ? .01 : 0));
        StockStrengthAnalysis result = MarketResearchCalculator.strength(stock("a"), own, benchmark);
        assertNotNull(result.getExcessReturnPp());
        assertFalse(result.getGaps().isEmpty());
        WatchlistResearchSnapshot snapshot = new WatchlistResearchSnapshot();
        snapshot.getStrengths().add(result);
        assertTrue(WatchlistResearchRules.findings(snapshot).isEmpty());
        assertTrue(MarketResearchCalculator.relationships(List.of(stock("a"), stock("b")),
                Map.of("a", own, "b", own), benchmark).isEmpty());
        benchmark.setQualityStatus("UNVERIFIED");
        assertNull(MarketResearchCalculator.strength(stock("a"), own, benchmark).getBeta());
    }

    @Test
    void doesNotReportUndefinedResidualCorrelationsOrStaleRelationships() {
        DailyBarHistory exact = prices(160, index -> market(index) * 2);
        assertTrue(MarketResearchCalculator.relationships(List.of(stock("a"), stock("b")),
                Map.of("a", exact, "b", exact), prices(160, MarketResearchCalculatorTest::market)).isEmpty());
        DailyBarHistory old = prices(159, index -> market(index) + Math.sin(index) * .002);
        DailyBarHistory full = prices(160, index -> market(index) + Math.sin(index) * .002);
        assertTrue(MarketResearchCalculator.relationships(List.of(stock("a"), stock("b")),
                Map.of("a", old, "b", full), prices(160, MarketResearchCalculatorTest::market)).isEmpty());
    }

    private static double market(int index) {
        return Math.sin(index * 1.13) * .012;
    }

    static WatchlistItem stock(String code) {
        WatchlistItem result = new WatchlistItem();
        result.setCode(code);
        result.setName("股票" + code);
        return result;
    }

    private static DailyBarHistory prices(int count, IntToDoubleFunction returns) {
        DailyBarHistory result = new DailyBarHistory();
        result.setQualityStatus("FRESH_PRIMARY");
        double price = 100;
        for (int index = 0; index <= count; index++) {
            if (index > 0) {
                price *= 1 + returns.applyAsDouble(index);
            }
            DailyBarPoint point = new DailyBarPoint();
            point.setTradeDate(LocalDate.of(2025, 1, 1).plusDays(index));
            point.setClose(BigDecimal.valueOf(price));
            point.setAdjustment("QFQ");
            point.setIsComplete(true);
            result.getBars().add(point);
        }
        return result;
    }
}
