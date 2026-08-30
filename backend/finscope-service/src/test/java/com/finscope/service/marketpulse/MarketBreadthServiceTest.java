package com.finscope.service.marketpulse;

import com.finscope.domain.marketpulse.MarketBreadthSnapshot;
import com.finscope.domain.marketpulse.MarketInternalHistoryPoint;
import com.finscope.domain.marketpulse.MarketIndexPerformance;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.rpc.marketpulse.MarketBreadthSource;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketBreadthServiceTest {

    @Test
    void enrichesBreadthWithFivePointInTimeIndexPerformances() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        MarketBreadthSource breadthSource = mock(MarketBreadthSource.class);
        QuantDailyBarSource bars = mock(QuantDailyBarSource.class);
        when(breadthSource.fetch(date)).thenReturn(breadth(date, 0.63D));
        when(bars.fetch(anyString(), anyInt())).thenReturn(batch(date));
        MarketBreadthService service = new MarketBreadthService();
        ReflectionTestUtils.setField(service, "breadthSource", breadthSource);
        ReflectionTestUtils.setField(service, "dailyBarSource", bars);

        MarketBreadthSnapshot result = service.calculate(date);

        assertEquals(5, result.getIndices().size());
        for (MarketIndexPerformance index : result.getIndices()) {
            assertEquals(date, index.getBusinessDate());
            assertTrue(index.getReturn1d() > 0D);
            assertTrue(index.getReturn5d() > 0D);
            assertTrue(index.getReturn20d() > 0D);
        }
        assertTrue(result.getInterpretation().contains("共振"));
    }

    @Test
    void rejectsIndexBarsFromAnotherBusinessDateWithoutMixingSnapshots() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        MarketBreadthSource breadthSource = mock(MarketBreadthSource.class);
        QuantDailyBarSource bars = mock(QuantDailyBarSource.class);
        when(breadthSource.fetch(date)).thenReturn(breadth(date, 0.35D));
        when(bars.fetch(anyString(), anyInt())).thenReturn(batch(date.minusDays(1)));
        MarketBreadthService service = new MarketBreadthService();
        ReflectionTestUtils.setField(service, "breadthSource", breadthSource);
        ReflectionTestUtils.setField(service, "dailyBarSource", bars);

        MarketBreadthSnapshot result = service.calculate(date);

        assertEquals(0, result.getIndices().size());
        assertTrue(result.getWarnings().stream().anyMatch(value -> value.contains("业务日期不一致")));
    }

    @Test
    void calculatesHistoricalIndexFromBarsBoundedByTheRequestedDate() {
        LocalDate latest = LocalDate.of(2026, 8, 21);
        LocalDate requested = LocalDate.of(2026, 8, 20);
        MarketBreadthSource breadthSource = mock(MarketBreadthSource.class);
        QuantDailyBarSource bars = mock(QuantDailyBarSource.class);
        when(breadthSource.fetch(requested)).thenReturn(breadth(requested, 0.55D));
        when(bars.fetch(anyString(), anyInt())).thenReturn(batch(latest));
        MarketBreadthService service = new MarketBreadthService();
        ReflectionTestUtils.setField(service, "breadthSource", breadthSource);
        ReflectionTestUtils.setField(service, "dailyBarSource", bars);

        MarketBreadthSnapshot result = service.calculate(requested);

        assertEquals(5, result.getIndices().size());
        assertEquals(requested, result.getIndices().get(0).getBusinessDate());
    }

    @Test
    void generatesChangeSummaryFromAdjacentMarketInternalPoints() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        MarketBreadthSource breadthSource = mock(MarketBreadthSource.class);
        QuantDailyBarSource bars = mock(QuantDailyBarSource.class);
        MarketBreadthSnapshot breadth = breadth(date, 0.63D);
        breadth.getHistory().add(internal(date.minusDays(1), 0.45D, 0.48D,
                2_000_000_000_000D, 30, 40, -500, 0.42D, -18D));
        breadth.getHistory().add(internal(date, 0.63D, 0.61D,
                2_300_000_000_000D, 88, 23, 1400, 0.67D, 42.5D));
        when(breadthSource.fetch(date)).thenReturn(breadth);
        when(bars.fetch(anyString(), anyInt())).thenReturn(batch(date));
        MarketBreadthService service = new MarketBreadthService();
        ReflectionTestUtils.setField(service, "breadthSource", breadthSource);
        ReflectionTestUtils.setField(service, "dailyBarSource", bars);

        MarketBreadthSnapshot result = service.calculate(date);

        assertEquals(date.minusDays(1), result.getChangeSummary().getPreviousBusinessDate());
        assertEquals(0.18D, result.getChangeSummary().getAdvanceRatioChange(), 0.000001D);
        assertEquals(0.13D, result.getChangeSummary().getMa20RatioChange(), 0.000001D);
        assertEquals(0.15D, result.getChangeSummary().getTotalAmountChangeRatio(), 0.000001D);
        assertEquals(75, result.getChangeSummary().getNewHighLowBalanceChange());
        assertEquals(1900, result.getChangeSummary().getNetAdvancesChange());
        assertEquals(0.25D, result.getChangeSummary().getAdvanceAmountRatioChange(), 0.000001D);
        assertEquals(60.5D, result.getChangeSummary().getMcclellanOscillatorChange(), 0.000001D);
        assertTrue(result.getChangeSummary().getHeadline().contains("扩散"));
        assertTrue(result.getChangeSummary().getChanges().stream()
                .anyMatch(value -> value.contains("MA20")));
        assertTrue(result.getChangeSummary().getChanges().stream()
                .anyMatch(value -> value.contains("上涨成交额占比")));
        assertTrue(result.getChangeSummary().getChanges().stream()
                .anyMatch(value -> value.contains("宽度动量")));
    }

    private MarketBreadthSnapshot breadth(LocalDate date, double ratio) {
        MarketBreadthSnapshot value = new MarketBreadthSnapshot();
        value.setBusinessDate(date);
        value.setSourceCode("TEST");
        value.setSourceFamily("TEST");
        value.setQualityStatus("FRESH_PRIMARY");
        value.setAdvanceCount((int) Math.round(5000 * ratio));
        value.setDeclineCount(5000 - value.getAdvanceCount());
        value.setFlatCount(0);
        value.setValidCount(5000);
        value.setAdvanceRatio(ratio);
        value.setTotalAmount(2_300_000_000_000D);
        value.setLimitUpCount(68);
        value.setLimitDownCount(4);
        value.setMedianChangePct(0.7D);
        return value;
    }

    private QuantDailyBarBatch batch(LocalDate date) {
        List<QuantDailyBar> values = new ArrayList<>();
        for (int index = 24; index >= 0; index--) {
            QuantDailyBar bar = new QuantDailyBar();
            bar.setTradeDate(date.minusDays(index));
            bar.setClose(BigDecimal.valueOf(100D + 24 - index));
            bar.setOpen(bar.getClose());
            bar.setHigh(bar.getClose());
            bar.setLow(bar.getClose());
            bar.setVolume(BigDecimal.TEN);
            bar.setAmount(BigDecimal.valueOf(1_000_000));
            values.add(bar);
        }
        return new QuantDailyBarBatch(values, "TEST", "TEST", "FRESH_PRIMARY", date,
                Collections.emptyList());
    }

    private MarketInternalHistoryPoint internal(LocalDate date, double advanceRatio,
                                                double ma20Ratio, double totalAmount,
                                                int high20, int low20, int netAdvances,
                                                double advanceAmountRatio,
                                                double mcclellanOscillator) {
        MarketInternalHistoryPoint value = new MarketInternalHistoryPoint();
        value.setBusinessDate(date);
        value.setAdvanceRatio(advanceRatio);
        value.setMa20Ratio(ma20Ratio);
        value.setTotalAmount(totalAmount);
        value.setMedianChangePct(advanceRatio - 0.5D);
        value.setNewHigh20Count(high20);
        value.setNewLow20Count(low20);
        value.setNetAdvances(netAdvances);
        value.setAdvanceAmountRatio(advanceAmountRatio);
        value.setMcclellanOscillator(mcclellanOscillator);
        return value;
    }
}
